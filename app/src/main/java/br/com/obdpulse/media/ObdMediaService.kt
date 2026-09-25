package br.com.obdpulse.media

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.service.media.MediaBrowserService
import androidx.core.graphics.createBitmap
import br.com.obdpulse.ObdManager
import br.com.obdpulse.Prefs
import br.com.obdpulse.R
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus
import br.com.obdpulse.service.ObdService
import br.com.obdpulse.ui.MainActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ObdMediaService : MediaBrowserService() {

    private val scope = MainScope()
    private lateinit var session: MediaSession
    private var art: Bitmap? = null
    private var lastRender = 0L
    private var lastChildren = 0L
    private var renderPending = false
    private var lastStatus: ObdStatus? = null
    private var lastDtcReads = -1
    private var lastDtcLoading = false
    private var lastMetaSignature: String? = null

    internal var lastPlaybackState: PlaybackState? = null
        private set
    internal var lastMetadata: MediaMetadata? = null
        private set

    override fun onCreate() {
        super.onCreate()
        art = renderArt()
        session = MediaSession(this, "OBD Pulse").apply {
            setCallback(callback)
            setSessionActivity(
                PendingIntent.getActivity(
                    this@ObdMediaService, 0, Intent(this@ObdMediaService, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            isActive = true
        }
        sessionToken = session.sessionToken
        render(ObdManager.state.value)
        scope.launch { ObdManager.state.collect { scheduleRender() } }
    }

    override fun onDestroy() {
        scope.cancel()
        session.release()
        super.onDestroy()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        if (rootHints?.getBoolean(BrowserRoot.EXTRA_RECENT) == true) return null
        val extras = Bundle().apply {
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
        }
        return BrowserRoot(MediaContent.ROOT, extras)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        val state = ObdManager.state.value
        if (parentId == MediaContent.DTC && state.status == ObdStatus.CONNECTED && state.dtcs == null && !state.dtcLoading) {
            ObdManager.requestDtcs()
        }
        result.sendResult(MediaContent.children(parentId, state, Prefs.favorites(this)).map(::toItem).toMutableList())
    }

    internal val callback = object : MediaSession.Callback() {
        override fun onPlay() = connect()

        override fun onPause() = ObdManager.disconnect()

        override fun onStop() = ObdManager.disconnect()

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            when {
                mediaId == MediaContent.STATUS ->
                    if (ObdManager.state.value.isActive) ObdManager.disconnect() else connect()
                mediaId.startsWith(MediaContent.VALUE_PREFIX) ->
                    if (!ObdManager.state.value.isActive) connect()
                mediaId.startsWith(MediaContent.DTC) -> ObdManager.requestDtcs()
            }
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            if (!ObdManager.state.value.isActive) connect()
        }
    }

    private fun connect() {
        val address = Prefs.address(this)
        if (address == null) {
            publish(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_ERROR, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f)
                    .setErrorMessage(getString(R.string.car_select_device))
                    .setActions(TRANSPORT_ACTIONS)
                    .build(),
            )
            return
        }
        ObdService.start(this, address)
    }

    private fun scheduleRender() {
        if (renderPending) return
        renderPending = true
        val wait = (lastRender + RENDER_INTERVAL - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        scope.launch {
            delay(wait)
            renderPending = false
            render(ObdManager.state.value)
        }
    }

    private fun render(state: ObdState) {
        lastRender = SystemClock.elapsedRealtime()
        val nowPlaying = MediaContent.stableNowPlaying(state)
        val signature = "${nowPlaying.title}|${nowPlaying.subtitle}|${nowPlaying.album}"
        if (signature != lastMetaSignature) {
            lastMetaSignature = signature
            val metadata =
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, MediaContent.STATUS)
                    .putString(MediaMetadata.METADATA_KEY_TITLE, nowPlaying.title)
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, nowPlaying.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, nowPlaying.subtitle)
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, nowPlaying.subtitle)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, nowPlaying.album)
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, nowPlaying.album)
                    .apply { art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                    .build()
            lastMetadata = metadata
            session.setMetadata(metadata)
        }
        publish(playbackState(state))

        val dtcChanged = state.dtcReadCount != lastDtcReads || state.dtcLoading != lastDtcLoading
        if (dtcChanged || state.status != lastStatus || lastRender - lastChildren >= CHILDREN_INTERVAL) {
            lastChildren = lastRender
            notifyChildrenChanged(MediaContent.ROOT)
            notifyChildrenChanged(MediaContent.DASHBOARD)
            notifyChildrenChanged(MediaContent.ALL)
            if (dtcChanged || state.status != lastStatus) notifyChildrenChanged(MediaContent.DTC)
        }
        lastStatus = state.status
        lastDtcReads = state.dtcReadCount
        lastDtcLoading = state.dtcLoading
    }

    private fun publish(playback: PlaybackState) {
        lastPlaybackState = playback
        session.setPlaybackState(playback)
    }

    private fun playbackState(state: ObdState): PlaybackState {
        val builder = PlaybackState.Builder()
        val position = PlaybackState.PLAYBACK_POSITION_UNKNOWN
        when (state.status) {
            ObdStatus.CONNECTED -> builder
                .setState(PlaybackState.STATE_PLAYING, position, 0f)
                .setActions(TRANSPORT_ACTIONS)
            ObdStatus.CONNECTING, ObdStatus.INITIALIZING -> builder
                .setState(PlaybackState.STATE_CONNECTING, position, 0f)
                .setActions(TRANSPORT_ACTIONS)
            ObdStatus.ERROR -> builder
                .setState(PlaybackState.STATE_ERROR, position, 0f)
                .setErrorMessage(state.message ?: getString(R.string.status_error, ""))
                .setActions(TRANSPORT_ACTIONS)
            ObdStatus.DISCONNECTED -> builder
                .setState(PlaybackState.STATE_PAUSED, position, 0f)
                .setActions(TRANSPORT_ACTIONS)
        }
        return builder.build()
    }

    private fun toItem(entry: MediaEntry): MediaBrowser.MediaItem {
        val description = MediaDescription.Builder()
            .setMediaId(entry.id)
            .setTitle(entry.title)
            .setSubtitle(entry.subtitle)
            .build()
        val flags = if (entry.browsable) MediaBrowser.MediaItem.FLAG_BROWSABLE else MediaBrowser.MediaItem.FLAG_PLAYABLE
        return MediaBrowser.MediaItem(description, flags)
    }

    private fun renderArt(): Bitmap? {
        val drawable = getDrawable(R.drawable.ic_launcher) ?: return null
        val bitmap = createBitmap(ART_SIZE, ART_SIZE)
        drawable.setBounds(0, 0, ART_SIZE, ART_SIZE)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private companion object {
        val TRANSPORT_ACTIONS = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or PlaybackState.ACTION_PLAY_FROM_SEARCH
        const val RENDER_INTERVAL = 1_000L
        const val CHILDREN_INTERVAL = 2_000L
        const val ART_SIZE = 96
        const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        const val CONTENT_STYLE_LIST = 1
    }
}
