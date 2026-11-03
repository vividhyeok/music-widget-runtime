// Session subscription lifecycle adapted from Meo98/media-widget (MIT, see docs/UPSTREAM-LICENSE.txt).
package dev.mwidget.runtime

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.view.KeyEvent

object Sessions {
    fun component(context: Context) = ComponentName(context, NotifListener::class.java)
    fun allowed(context: Context) = context.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(component(context))

    fun list(context: Context): List<MediaController> {
        if (!allowed(context)) return emptyList()
        return try {
            context.getSystemService(MediaSessionManager::class.java).getActiveSessions(component(context))
                .sortedByDescending { c ->
                    when {
                        playing(c.playbackState) -> 3
                        c.playbackState?.state == PlaybackState.STATE_PAUSED -> 2
                        c.metadata != null -> 1
                        else -> 0
                    }
                }.distinctBy { it.packageName }
        } catch (_: SecurityException) { emptyList() }
    }

    fun pick(sessions: List<MediaController>, pkg: String?) =
        if (pkg == null) sessions.firstOrNull() else sessions.firstOrNull { it.packageName == pkg }

    fun playing(state: PlaybackState?) = state?.state == PlaybackState.STATE_PLAYING ||
        state?.state == PlaybackState.STATE_BUFFERING && state.playbackSpeed > 0

    fun label(context: Context, pkg: String): String = when (pkg) {
        "com.google.android.apps.youtube.music" -> "YouTube Music"
        else -> runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    fun song(context: Context, controller: MediaController?, preferred: String?): Song {
        if (!allowed(context)) return Song(title="알림 접근을 허용하세요",artist="위젯 설정에서 권한을 열 수 있어요",status="권한 필요")
        if (controller == null) return if (preferred == null) Song() else
            Song(title="선택한 플레이어 대기 중",artist=label(context,preferred),status="세션 없음")
        val metadata=controller.metadata
        val state=controller.playbackState
        fun value(key: String) = metadata?.getString(key)?.takeIf { it.isNotBlank() }
        val image=sequenceOf(MediaMetadata.METADATA_KEY_ALBUM_ART,MediaMetadata.METADATA_KEY_ART,MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            .mapNotNull { metadata?.getBitmap(it) }.firstOrNull { !it.isRecycled }
        val art=if (image?.config == Bitmap.Config.HARDWARE) image.copy(Bitmap.Config.ARGB_8888,false) else image
        return Song(
            value(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: value(MediaMetadata.METADATA_KEY_TITLE) ?: "제목 없음",
            value(MediaMetadata.METADATA_KEY_ARTIST) ?: value(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "아티스트 정보 없음",
            value(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(), label(context,controller.packageName),
            when(state?.state) {
                PlaybackState.STATE_PLAYING -> "재생 중"
                PlaybackState.STATE_PAUSED -> "일시 정지"
                PlaybackState.STATE_BUFFERING -> "버퍼링 중"
                PlaybackState.STATE_ERROR -> "재생 오류"
                else -> "대기 중"
            }, playing(state),art
        )
    }

    fun send(controller: MediaController, action: String): Boolean {
        val state = controller.playbackState
        val flags = state?.actions ?: 0
        val controls = controller.transportControls
        val key: Int
        when(action) {
            "previous" -> {
                if (flags and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) { controls.skipToPrevious(); return true }
                key = KeyEvent.KEYCODE_MEDIA_PREVIOUS
            }
            "next" -> {
                if (flags and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) { controls.skipToNext(); return true }
                key = KeyEvent.KEYCODE_MEDIA_NEXT
            }
            "toggle" -> {
                if (playing(state) && flags and PlaybackState.ACTION_PAUSE != 0L) { controls.pause(); return true }
                if (!playing(state) && flags and PlaybackState.ACTION_PLAY != 0L) { controls.play(); return true }
                key = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            else -> return false
        }
        val down=controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN,key))
        val up=controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP,key))
        return down || up
    }
}

class NotifListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { MusicWidget.refresh(this) }
    private val callbacks = mutableMapOf<MediaSession.Token, Pair<MediaController,MediaController.Callback>>()
    private val manager by lazy { getSystemService(MediaSessionManager::class.java) }
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { bind(it.orEmpty()) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        stopTracking()
        try {
            val component = Sessions.component(this)
            manager.addOnActiveSessionsChangedListener(listener,component,handler)
            bind(manager.getActiveSessions(component))
        } catch (_: SecurityException) {
            stopTracking()
            schedule()
        }
    }

    private fun bind(controllers: List<MediaController>) {
        val active = controllers.map { it.sessionToken }.toSet()
        (callbacks.keys-active).forEach { token -> callbacks.remove(token)?.let { (c,cb) -> c.unregisterCallback(cb) } }
        controllers.forEach { c ->
            if (c.sessionToken !in callbacks) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = schedule()
                    override fun onPlaybackStateChanged(state: PlaybackState?) = schedule()
                    override fun onSessionDestroyed() = schedule()
                }
                c.registerCallback(callback,handler)
                callbacks[c.sessionToken] = c to callback
            }
        }
        schedule()
    }

    private fun schedule() {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh,120)
    }

    private fun stopTracking() {
        manager.removeOnActiveSessionsChangedListener(listener)
        callbacks.values.forEach { (c,cb) -> c.unregisterCallback(cb) }
        callbacks.clear()
        handler.removeCallbacks(refresh)
    }

    override fun onListenerDisconnected() {
        stopTracking()
        MusicWidget.refresh(this)
        super.onListenerDisconnected()
    }

    override fun onDestroy() { stopTracking(); super.onDestroy() }
}
