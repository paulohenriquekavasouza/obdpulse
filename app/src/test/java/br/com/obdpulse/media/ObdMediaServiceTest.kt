package br.com.obdpulse.media

import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Looper
import android.service.media.MediaBrowserService.BrowserRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ObdMediaServiceTest {

    @Test
    fun exposesListRootAndOnlyPlayPauseTransport() {
        val service = Robolectric.buildService(ObdMediaService::class.java).create().get()

        val root = service.onGetRoot("com.google.android.projection.gearhead", 0, null)!!
        assertEquals(MediaContent.ROOT, root.rootId)
        assertTrue(root.extras!!.getBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED"))
        assertEquals(1, root.extras!!.getInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"))
        assertNull(service.onGetRoot("com.android.systemui", 0, Bundle().apply { putBoolean(BrowserRoot.EXTRA_RECENT, true) }))

        assertNotNull(service.sessionToken)
        val playback = service.lastPlaybackState!!
        assertEquals(PlaybackState.STATE_PAUSED, playback.state)
        assertTrue(playback.actions and PlaybackState.ACTION_PLAY != 0L)
        assertTrue(playback.actions and PlaybackState.ACTION_PAUSE != 0L)
        assertEquals(0L, playback.actions and PlaybackState.ACTION_STOP)
        assertTrue(playback.customActions.isEmpty())
        assertEquals("OBD Pulse", service.lastMetadata!!.getString(MediaMetadata.METADATA_KEY_TITLE))
        assertNotNull(service.lastMetadata!!.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
    }

    @Test
    fun playWithoutSavedReaderShowsError() {
        val service = Robolectric.buildService(ObdMediaService::class.java).create().get()

        service.callback.onPlay()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(PlaybackState.STATE_ERROR, service.lastPlaybackState!!.state)
        assertEquals("Escolha o leitor no app do celular primeiro", service.lastPlaybackState!!.errorMessage.toString())
    }
}
