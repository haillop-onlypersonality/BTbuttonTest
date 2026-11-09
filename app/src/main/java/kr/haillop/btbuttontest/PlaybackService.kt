package kr.haillop.btbuttontest

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        DiagnosticLog.add(this, "SERVICE onCreate")
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    DiagnosticLog.add(this@PlaybackService, "PLAYER isPlaying=$isPlaying")
                }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    DiagnosticLog.add(this@PlaybackService, "PLAYER playWhenReady=$playWhenReady reason=$reason")
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val label = when (playbackState) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN"
                    }
                    DiagnosticLog.add(this@PlaybackService, "PLAYER state=$label")
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    DiagnosticLog.add(this@PlaybackService, "PLAYER item=${mediaItem?.mediaMetadata?.title} reason=$reason")
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    DiagnosticLog.add(this@PlaybackService, "PLAYER error code=${error.errorCodeName} message=${error.message}")
                }
            })
        }
        mediaSession = MediaSession.Builder(this, player)
            .setId("BTButtonTestSession")
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    DiagnosticLog.add(this@PlaybackService, "SESSION controllerConnected package=${controller.packageName} uid=${controller.uid}")
                    return super.onConnect(session, controller)
                }
            })
            .build()
        DiagnosticLog.add(this, "SESSION created Media3 active")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        DiagnosticLog.add(this, "SESSION onGetSession package=${controllerInfo.packageName} uid=${controllerInfo.uid}")
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DiagnosticLog.add(this, "SERVICE onStartCommand action=${intent?.action} flags=$flags startId=$startId")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        DiagnosticLog.add(this, "SERVICE onDestroy")
        mediaSession.release()
        player.release()
        super.onDestroy()
    }
}
