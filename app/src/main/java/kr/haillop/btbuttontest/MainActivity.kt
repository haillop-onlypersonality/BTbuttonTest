package kr.haillop.btbuttontest

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var synthesisPending = 0
    private var synthesisFailed = false
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val handler = Handler(Looper.getMainLooper())
    private val sentences = listOf("안녕하세요.", "좋은 하루 되세요.", "행복하세요.")
    private val audioFiles by lazy { sentences.indices.map { File(cacheDir, "bt_test_sentence_$it.wav") } }
    private val refresh = object : Runnable {
        override fun run() { refreshLogs(); handler.postDelayed(this, 700) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        DiagnosticLog.add(this, "APP onCreate Media3 test")
        tts = TextToSpeech(this, this)
        connectToPlaybackService()
        refreshLogs()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 20, 18, 12) }
        val title = TextView(this).apply { text = "Bluetooth Media Button Test 2"; textSize = 21f; gravity = Gravity.CENTER }
        statusView = TextView(this).apply { textSize = 14f; setPadding(0, 12, 0, 12) }
        root.addView(title)
        root.addView(statusView)
        addButton(root, "테스트 문장 재생") { prepareAndPlay() }
        addButton(root, "재생 / 일시정지") {
            val c = controller
            if (c == null) log("UI controllerNotConnected") else if (c.isPlaying) c.pause() else c.play()
        }
        addButton(root, "정지") { controller?.stop(); controller?.seekTo(0) }
        addButton(root, "로그 JSON 복사") { copyLogs() }
        addButton(root, "로그 초기화") { DiagnosticLog.clear(this); refreshLogs() }
        root.addView(TextView(this).apply { text = "수신 로그 (최신 항목이 아래에 표시됨)"; textSize = 14f; setPadding(0, 10, 0, 6) })
        val scroll = ScrollView(this)
        logView = TextView(this).apply { textSize = 11.5f; setTextIsSelectable(true); setPadding(6, 6, 6, 6) }
        scroll.addView(logView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply { text = label; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun connectToPlaybackService() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            runCatching { controllerFuture?.get() }.onSuccess { mediaController ->
                controller = mediaController
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { log("UI_PLAYER isPlaying=$isPlaying") }
                    override fun onPlaybackStateChanged(playbackState: Int) { log("UI_PLAYER playbackState=$playbackState") }
                })
                log("APP MediaController connected")
                updateStatus()
            }.onFailure { log("APP MediaController connectionError ${it.javaClass.simpleName}: ${it.message}") }
        }, mainExecutor)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) { log("TTS initFailed status=$status"); return }
        val result = tts.setLanguage(Locale.KOREAN)
        ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { log("TTS synthesisStart id=$utteranceId") }
            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    log("TTS synthesisDone id=$utteranceId")
                    synthesisPending--
                    if (synthesisPending == 0) finishSynthesis()
                }
            }
            @Deprecated("Deprecated in Android")
            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    synthesisFailed = true
                    synthesisPending--
                    log("TTS synthesisError id=$utteranceId")
                    if (synthesisPending == 0) finishSynthesis()
                }
            }
        })
        log("TTS ready=$ttsReady engine=${tts.defaultEngine} languageResult=$result")
        updateStatus()
    }

    private fun prepareAndPlay() {
        if (!ttsReady) { log("TTS rejected notReady"); return }
        if (controller == null) { log("PLAYER rejected controllerNotConnected"); return }
        synthesisPending = sentences.size
        synthesisFailed = false
        log("TTS synthesizeToFile begin count=${sentences.size}")
        sentences.forEachIndexed { index, sentence ->
            val file = audioFiles[index]
            file.delete()
            val id = "file-$index-${System.currentTimeMillis()}"
            val result = tts.synthesizeToFile(sentence, Bundle(), file, id)
            log("TTS synthesize request index=$index result=$result path=${file.name}")
            if (result == TextToSpeech.ERROR) { synthesisFailed = true; synthesisPending-- }
        }
        if (synthesisPending == 0) finishSynthesis()
    }

    private fun finishSynthesis() {
        if (synthesisFailed || audioFiles.any { !it.exists() || it.length() == 0L }) {
            log("TTS synthesisFailed files=${audioFiles.map { "${it.name}:${it.length()}" }}")
            Toast.makeText(this, "음성 파일 생성 실패. 로그를 확인해줘.", Toast.LENGTH_LONG).show()
            return
        }
        val items = audioFiles.mapIndexed { index, file ->
            MediaItem.Builder().setUri(Uri.fromFile(file)).setMediaMetadata(
                MediaMetadata.Builder().setTitle(sentences[index]).setArtist("Bluetooth Button Test").build()
            ).build()
        }
        val c = controller ?: return
        c.setMediaItems(items, 0, 0L)
        c.prepare()
        c.play()
        log("PLAYER playlistReady count=${items.size}; play requested")
        Toast.makeText(this, "테스트 문장 재생 시작", Toast.LENGTH_SHORT).show()
    }

    private fun copyLogs() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("BTButtonTest diagnostics", DiagnosticLog.json(this)))
        Toast.makeText(this, "JSON 로그를 복사했어.", Toast.LENGTH_SHORT).show()
        log("UI copyLogs")
    }

    private fun log(message: String) { DiagnosticLog.add(this, message); refreshLogs() }
    private fun refreshLogs() {
        if (!::logView.isInitialized) return
        logView.text = DiagnosticLog.json(this)
        updateStatus()
    }
    private fun updateStatus() {
        if (!::statusView.isInitialized) return
        val c = controller
        statusView.text = "TTS: ${if (ttsReady) "READY" else "INITIALIZING"}\nMedia3 Service: ${if (c != null) "CONNECTED" else "CONNECTING"}\nPlayer: ${when {
            c == null -> "DISCONNECTED"
            c.isPlaying -> "PLAYING"
            c.playbackState == Player.STATE_ENDED -> "ENDED"
            else -> "PAUSED / IDLE"
        }}\n이어폰 입력은 Media3 Player 상태 변화로 기록됨"
    }
    override fun onResume() { super.onResume(); handler.post(refresh); log("APP onResume") }
    override fun onPause() { log("APP onPause"); handler.removeCallbacks(refresh); super.onPause() }
    override fun onDestroy() {
        log("APP onDestroy")
        if (::tts.isInitialized) tts.shutdown()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onDestroy()
    }
}
