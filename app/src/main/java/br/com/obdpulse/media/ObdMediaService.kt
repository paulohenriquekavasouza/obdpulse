package br.com.obdpulse.media

import android.app.PendingIntent
import android.content.Intent
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.service.media.MediaBrowserService
import br.com.obdpulse.ObdManager
import br.com.obdpulse.Prefs
import br.com.obdpulse.R
import br.com.obdpulse.obd.Format
import br.com.obdpulse.obd.Labels
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
    private val gauge = GaugeRenderer()
    private var lastRender = 0L
    private var lastChildren = 0L
    private var renderPending = false
    private var lastStatus: ObdStatus? = null
    private var lastDtcReads = -1
    private var lastDtcLoading = false
    private var lastMetaSignature: String? = null
    private var lastSeenSub10: Long? = null
    private var flashText: String? = null
    private var flashUntil = 0L

    private var prefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    internal var lastPlaybackState: PlaybackState? = null
        private set
    internal var lastMetadata: MediaMetadata? = null
        private set

    override fun onCreate() {
        super.onCreate()
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
        prefsListener = Prefs.observe(this) { key ->
            when (key) {
                Prefs.KEY_ORDER, Prefs.KEY_FAVORITES -> notifyChildrenChanged(MediaContent.DASHBOARD)
                Prefs.KEY_CLUSTER -> {
                    notifyChildrenChanged(MediaContent.DASHBOARD)
                    notifyChildrenChanged(MediaContent.CLUSTER)
                    render(ObdManager.state.value)
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        prefsListener?.let { Prefs.removeObserver(this, it) }
        session.release()
        super.onDestroy()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        if (rootHints?.getBoolean(BrowserRoot.EXTRA_RECENT) == true) return null
        maybeAutoConnect()
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
        val children = MediaContent.children(
            parentId, state, Prefs.favorites(this), Prefs.clusterMetric(this), Prefs.order(this),
        )
        result.sendResult(children.map(::toItem).toMutableList())
    }

    internal val callback = object : MediaSession.Callback() {
        override fun onPlay() = connect()

        override fun onPause() = ObdManager.disconnect()

        override fun onStop() = ObdManager.disconnect()

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            when {
                mediaId == MediaContent.STATUS ->
                    if (ObdManager.state.value.isActive) ObdManager.disconnect() else connect()
                mediaId.startsWith(MediaContent.CLUSTER_PREFIX) -> {
                    Prefs.saveClusterMetric(this@ObdMediaService, mediaId.removePrefix(MediaContent.CLUSTER_PREFIX))
                    notifyChildrenChanged(MediaContent.CLUSTER)
                    notifyChildrenChanged(MediaContent.DASHBOARD)
                    render(ObdManager.state.value)
                }
                mediaId.startsWith(MediaContent.VALUE_PREFIX) ->
                    if (!ObdManager.state.value.isActive) connect()
                mediaId.startsWith(MediaContent.DTC) -> ObdManager.requestDtcs()
            }
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            if (!ObdManager.state.value.isActive) connect()
        }
    }

    private fun maybeAutoConnect() {
        if (!Prefs.autoConnect(this)) return
        if (ObdManager.state.value.isActive) return
        val address = Prefs.address(this) ?: return
        ObdService.start(this, address)
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
        val tick = (lastRender / ROTATE_MS).toInt()
        val data = GaugeData.from(state, tick)
        val title = clusterTitle(state)
        val signature = "$title|${data.signature()}"
        if (signature != lastMetaSignature) {
            lastMetaSignature = signature
            val art = gauge.render(data)
            val metadata =
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, MediaContent.STATUS)
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, data.subtitle)
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, data.subtitle)
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                    .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, art)
                    .build()
            lastMetadata = metadata
            session.setMetadata(metadata)
        }
        publish(playbackState(state))

        val dtcChanged = state.dtcReadCount != lastDtcReads || state.dtcLoading != lastDtcLoading
        val statusChanged = state.status != lastStatus
        if (statusChanged || lastRender - lastChildren >= CHILDREN_INTERVAL) {
            lastChildren = lastRender
            notifyChildrenChanged(MediaContent.DASHBOARD)
            notifyChildrenChanged(MediaContent.PERFORMANCE)
            notifyChildrenChanged(MediaContent.TURBO)
        }
        if (statusChanged) notifyChildrenChanged(MediaContent.ALL)
        if (statusChanged || dtcChanged) {
            notifyChildrenChanged(MediaContent.ROOT)
            notifyChildrenChanged(MediaContent.DTC)
        }
        lastStatus = state.status
        lastDtcReads = state.dtcReadCount
        lastDtcLoading = state.dtcLoading
    }

    private fun clusterTitle(state: ObdState): String {
        val now = SystemClock.elapsedRealtime()
        val metric = Prefs.clusterMetric(this)
        val sub10 = state.trip.lastSub10Ms
        if (metric.isBlank() && sub10 != null && sub10 != lastSeenSub10) {
            flashText = "0–100: ${Format.number(sub10 / 1000.0, 1)} s"
            flashUntil = now + FLASH_MS
        }
        lastSeenSub10 = sub10

        if (metric.isNotBlank()) {
            if (state.status == ObdStatus.CONNECTED) {
                state.values.firstOrNull { it.key == metric }?.let {
                    return "${Labels.short(metric)} ${it.text} ${it.unit}".trim()
                }
            }
            return TITLE
        }
        flashText?.let { if (now < flashUntil) return it }
        return TITLE
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

    private companion object {
        const val TITLE = "OBD Pulse"
        val TRANSPORT_ACTIONS = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or PlaybackState.ACTION_PLAY_FROM_SEARCH
        const val RENDER_INTERVAL = 200L
        const val CHILDREN_INTERVAL = 1_000L
        const val FLASH_MS = 6_000L
        const val ROTATE_MS = 3_000L
        const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        const val CONTENT_STYLE_LIST = 1
    }
}
