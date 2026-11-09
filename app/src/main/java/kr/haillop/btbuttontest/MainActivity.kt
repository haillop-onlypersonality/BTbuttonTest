package kr.haillop.btbuttontest

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var session: MediaSessionCompat
    private lateinit var logView: TextView
    private lateinit var stateView: TextView
    private lateinit var tts: TextToSpeech
    private val logs = mutableListOf<String>()
    private val sentences = listOf("안녕하세요.", "좋은 하루 되세요.", "행복하세요.")
    private var currentIndex = 0
    private var sessionEnabled = true
    private var playing = false
    private var ttsReady = false
    private var utteranceSerial = 0
    private var currentUtteranceId: String? = null
    private var destroyed = false

    private val logFile: File by lazy { File(filesDir, "diagnostic_logs.json") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadLogs()
        buildScreen()
        addLog("APP onCreate")
        initializeSession()
        tts = TextToSpeech(this, this)
        updateScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 16)
        }
        val title = TextView(this).apply {
            text = "Bluetooth Media Button Test"
            textSize = 22f
            gravity = Gravity.CENTER
        }
        stateView = TextView(this).apply {
            textSize = 15f
            setPadding(0, 16, 0, 16)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(stateView, LinearLayout.LayoutParams(-1, -2))
        addButton(root, "테스트 문장 재생") { startSpeech() }
        addButton(root, "재생 / 일시정지") { toggleSpeech() }
        addButton(root, "정지") { stopSpeech() }
        addButton(root, "미디어 세션 활성화 / 비활성화") {
            sessionEnabled = !sessionEnabled
            session.isActive = sessionEnabled
            addLog("UI sessionActive=$sessionEnabled")
            updatePlaybackState()
            updateScreen()
        }
        addButton(root, "로그 JSON 복사") { copyLogs() }
        addButton(root, "로그 초기화") {
            logs.clear()
            persistLogs()
            addLog("LOG cleared")
        }

        val label = TextView(this).apply {
            text = "수신 로그 (최신 항목이 아래에 표시됨)"
            textSize = 14f
            setPadding(0, 14, 0, 8)
        }
        root.addView(label, LinearLayout.LayoutParams(-1, -2))
        val scroll = ScrollView(this)
        logView = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(8, 8, 8, 8)
        }
        scroll.addView(logView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2))
    }

    private fun initializeSession() {
        session = MediaSessionCompat(this, "BTButtonTest").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: android.content.Intent): Boolean {
                    @Suppress("DEPRECATION")
                    val event = mediaButtonEvent.getParcelableExtra("android.intent.extra.KEY_EVENT") as? KeyEvent
                    addLog("CALLBACK onMediaButtonEvent action=${event?.action} keyCode=${event?.keyCode} repeat=${event?.repeatCount} device=${event?.deviceId}")
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }

                override fun onPlay() {
                    addLog("CALLBACK onPlay")
                    resumeSpeech()
                }

                override fun onPause() {
                    addLog("CALLBACK onPause")
                    pauseSpeech()
                }

                override fun onStop() {
                    addLog("CALLBACK onStop")
                    stopSpeech()
                }

                override fun onSkipToNext() {
                    addLog("CALLBACK onSkipToNext")
                    if (currentIndex < sentences.lastIndex) {
                        currentIndex++
                        if (playing) speakCurrentSentence()
                    }
                }

                override fun onSkipToPrevious() {
                    addLog("CALLBACK onSkipToPrevious")
                    currentIndex = (currentIndex - 1).coerceAtLeast(0)
                    if (playing) speakCurrentSentence()
                }
            })
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "안녕하세요. 좋은 하루 되세요. 행복하세요.")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "BTButtonTest TTS")
                    .build()
            )
            isActive = true
        }
        sessionEnabled = true
        addLog("SESSION initialized active=${session.isActive}")
        updatePlaybackState()
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            addLog("TTS initFailed status=$status")
            return
        }
        val languageResult = tts.setLanguage(Locale.KOREAN)
        ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
            languageResult != TextToSpeech.LANG_NOT_SUPPORTED
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                addLog("TTS onStart id=$utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    if (utteranceId == currentUtteranceId && playing) {
                        addLog("TTS onDone id=$utteranceId")
                        currentIndex++
                        speakCurrentSentence()
                    }
                }
            }

            @Deprecated("Deprecated in Android")
            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    addLog("TTS onError id=$utteranceId")
                    if (utteranceId == currentUtteranceId) {
                        playing = false
                        currentUtteranceId = null
                        updatePlaybackState()
                    }
                }
            }
        })
        addLog("TTS initialized ready=$ttsReady languageResult=$languageResult engine=${tts.defaultEngine}")
        updateScreen()
    }

    private fun startSpeech() {
        if (!ttsReady) {
            addLog("TTS startRejected notReady")
            Toast.makeText(this, "TTS 준비 중이거나 한국어 음성이 없어.", Toast.LENGTH_SHORT).show()
            return
        }
        currentIndex = 0
        playing = true
        addLog("TTS start")
        updatePlaybackState()
        speakCurrentSentence()
    }

    private fun toggleSpeech() {
        if (playing) pauseSpeech() else resumeSpeech()
    }

    private fun resumeSpeech() {
        if (!ttsReady) {
            addLog("TTS resumeRejected notReady")
            return
        }
        if (playing) {
            addLog("TTS resumeIgnored alreadyPlaying")
            return
        }
        if (currentIndex >= sentences.size) currentIndex = 0
        playing = true
        addLog("TTS resume sentenceIndex=$currentIndex")
        updatePlaybackState()
        speakCurrentSentence()
    }

    private fun pauseSpeech() {
        if (!playing) {
            addLog("TTS pauseIgnored alreadyPaused")
            return
        }
        playing = false
        tts.stop()
        currentUtteranceId = null
        addLog("TTS paused sentenceIndex=$currentIndex (resume restarts current sentence)")
        updatePlaybackState()
        updateScreen()
    }

    private fun stopSpeech() {
        tts.stop()
        playing = false
        currentIndex = 0
        currentUtteranceId = null
        addLog("TTS stopped")
        updatePlaybackState()
        updateScreen()
    }

    private fun speakCurrentSentence() {
        if (!playing || !ttsReady) return
        if (currentIndex >= sentences.size) {
            playing = false
            currentUtteranceId = null
            addLog("TTS completed")
            updatePlaybackState()
            updateScreen()
            return
        }
        val id = "sentence-${currentIndex}-${++utteranceSerial}"
        currentUtteranceId = id
        addLog("TTS speak index=$currentIndex text=${sentences[currentIndex]} id=$id")
        val result = tts.speak(sentences[currentIndex], TextToSpeech.QUEUE_FLUSH, null, id)
        if (result == TextToSpeech.ERROR) {
            playing = false
            addLog("TTS speakError index=$currentIndex")
            updatePlaybackState()
        }
        updateScreen()
    }

    private fun updatePlaybackState() {
        if (!::session.isInitialized) return
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (playing) 1.0f else 0.0f)
                .build()
        )
        addLog("SESSION playbackState=${if (playing) "PLAYING" else "PAUSED"} active=${session.isActive}")
        updateScreen()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        addLog("ACTIVITY dispatchKeyEvent action=${event.action} keyCode=${event.keyCode} repeat=${event.repeatCount} device=${event.deviceId} source=${event.source}")
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        addLog("ACTIVITY onKeyDown keyCode=$keyCode action=${event.action} repeat=${event.repeatCount}")
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        addLog("ACTIVITY onKeyUp keyCode=$keyCode action=${event.action} repeat=${event.repeatCount}")
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        addLog("APP onResume sessionActive=${if (::session.isInitialized) session.isActive else false}")
    }

    override fun onPause() {
        addLog("APP onPause sessionActive=${if (::session.isInitialized) session.isActive else false}")
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        addLog("APP onDestroy")
        if (::session.isInitialized) {
            session.isActive = false
            session.release()
        }
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }

    private fun addLog(message: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
        logs.add("$stamp $message")
        while (logs.size > 500) logs.removeAt(0)
        persistLogs()
        if (::logView.isInitialized) {
            logView.text = logs.joinToString("\n")
            logView.post { (logView.parent as? ScrollView)?.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun loadLogs() {
        if (!logFile.exists()) return
        runCatching {
            val json = JSONObject(logFile.readText())
            val array = json.optJSONArray("logs") ?: JSONArray()
            for (i in 0 until array.length()) logs.add(array.optString(i))
        }.onFailure {
            logs.clear()
            logs.add("LOG loadError=${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun persistLogs() {
        runCatching {
            val array = JSONArray()
            logs.forEach { array.put(it) }
            val json = JSONObject()
                .put("schemaVersion", 1)
                .put("updatedAt", System.currentTimeMillis())
                .put("logs", array)
            logFile.writeText(json.toString(2))
        }
    }

    private fun copyLogs() {
        val text = runCatching { logFile.readText() }.getOrElse { "{}" }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("BTButtonTest diagnostics", text))
        Toast.makeText(this, "JSON 로그를 복사했어.", Toast.LENGTH_SHORT).show()
        addLog("UI copyLogs")
    }

    private fun updateScreen() {
        if (!::stateView.isInitialized) return
        stateView.text = "TTS: ${if (ttsReady) "READY" else "INITIALIZING"}\nMediaSession: ${if (sessionEnabled) "ACTIVE" else "INACTIVE"}\nPlaybackState: ${if (playing) "PLAYING" else "PAUSED"}\n문장: ${currentIndex + 1}/${sentences.size}\n로그 파일: files/diagnostic_logs.json"
        if (::logView.isInitialized) logView.text = logs.joinToString("\n")
    }
}
