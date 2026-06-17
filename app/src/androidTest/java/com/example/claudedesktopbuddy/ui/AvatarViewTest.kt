package com.example.claudedesktopbuddy.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import com.example.claudedesktopbuddy.buddy.BuddyState
import com.example.claudedesktopbuddy.device.AndroidCharacterPackProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * On-device render check for the avatar: writes a real (tiny) GIF pack to app storage, resolves it
 * through the provider, and composes [AvatarView]. Asserting the avatar node exists proves the whole
 * UI pipeline wires up on a device — Coil's file-backed [coil3.ImageLoader] with the GIF decoder is
 * built, the `File` model is accepted, and the composable lays out without throwing.
 */
class AvatarViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packRoot = File(context.filesDir, "character-packs")

    @Before
    fun stagePack() {
        packRoot.deleteRecursively()
        val dir = File(packRoot, "uitest").apply { mkdirs() }
        File(dir, "idle.gif").writeBytes(ONE_PIXEL_GIF)
        File(dir, "manifest.json").writeText("""{"name":"uitest","states":{"idle":"idle.gif"}}""")
    }

    @After
    fun tidy() {
        packRoot.deleteRecursively()
    }

    @Test
    fun renders_the_avatar_for_a_pushed_pack() {
        val pack = AndroidCharacterPackProvider(context).activePack()
        assertNotNull("the staged pack must resolve", pack)

        composeRule.setContent {
            AvatarView(pack = pack!!, state = BuddyState(isConnected = true))
        }

        composeRule.onNodeWithContentDescription("Buddy avatar").assertExists()
    }

    private companion object {
        /** A minimal valid 1x1 GIF89a (single red pixel) — enough to exercise the decoder path. */
        val ONE_PIXEL_GIF = intArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80, 0x00, 0x00,
            0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00,
            0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3B,
        ).map { it.toByte() }.toByteArray()
    }
}
