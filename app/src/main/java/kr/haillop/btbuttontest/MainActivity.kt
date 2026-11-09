package kr.haillop.btbuttontest

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.media.session.MediaButtonReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var session: MediaSessionCompat
    private lateinit var logView: TextView
    private lateinit var stateView: TextView
    private val logs = mutableListOf<String>()
    private var sessionEnabled = false
    private var playing = true

    private val logFile: File by lazy { File(filesDir, "diagnostic_logs.json") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadLogs()
        buildScreen()
        addLog("APP onCreate savedInstanceState=${savedInstanceState != null}")
        initializeSession()
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

        addButton(root, "세션 활성화 / 비활성화") {
            sessionEnabled = !sessionEnabled
            session.isActive = sessionEnabled
            addLog("UI sessionActive=$sessionEnabled")
            updatePlaybackState()
            updateScreen()
        }
        addButton(root, "재생 상태 전환 (PLAYING / PAUSED)") {
            playing = !playing
            addLog("UI playing=$playing")
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
                    val event = mediaButtonEvent.getParcelableExtra(IntentKeyEvent) as? KeyEvent
                    addLog("CALLBACK onMediaButtonEvent action=${event?.action} keyCode=${event?.keyCode} repeat=${event?.repeatCount} device=${event?.deviceId}")
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }

                override fun onPlay() {
                    addLog("CALLBACK onPlay")
                    playing = true
                    updatePlaybackState()
                    updateScreen()
                }

                override fun onPause() {
                    addLog("CALLBACK onPause")
                    playing = false
                    updatePlaybackState()
                    updateScreen()
                }

                override fun onStop() {
                    addLog("CALLBACK onStop")
                    playing = false
                    updatePlaybackState()
                    updateScreen()
                }

                override fun onSkipToNext() {
                    addLog("CALLBACK onSkipToNext")
                }

                override fun onSkipToPrevious() {
                    addLog("CALLBACK onSkipToPrevious")
                }

                override fun onSeekTo(pos: Long) {
                    addLog("CALLBACK onSeekTo position=$pos")
                }
            })
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Bluetooth input test")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "BTButtonTest")
                    .build()
            )
            isActive = true
        }
        sessionEnabled = true
        addLog("SESSION initialized active=${session.isActive}")
        updatePlaybackState()
    }

    private fun updatePlaybackState() {
        if (!::session.isInitialized) return
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .setActiveQueueItemId(0)
                .build()
        )
        addLog("SESSION playbackState=${if (playing) "PLAYING" else "PAUSED"} active=${session.isActive}")
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
        addLog("APP onDestroy")
        if (::session.isInitialized) {
            session.isActive = false
            session.release()
        }
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
        stateView.text = "MediaSession: ${if (sessionEnabled) "ACTIVE" else "INACTIVE"}\nPlaybackState: ${if (playing) "PLAYING" else "PAUSED"}\n로그 파일: files/diagnostic_logs.json"
        if (::logView.isInitialized) logView.text = logs.joinToString("\n")
    }

    companion object {
        private const val IntentKeyEvent = "android.intent.extra.KEY_EVENT"
    }
}
