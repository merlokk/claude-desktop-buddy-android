package com.example.claudedesktopbuddy.device

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.claudedesktopbuddy.buddy.CharacterPackReceiver
import com.example.claudedesktopbuddy.protocol.InboundMessage.FolderPush
import java.io.File
import kotlin.io.encoding.Base64
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device test of the real folder-push write path: drives [AndroidCharacterPackSink] through a
 * [CharacterPackReceiver] and asserts the pushed files actually appear under the app's private
 * storage with the right bytes, that nested paths are created, and that an escaping path is dropped.
 */
@RunWith(AndroidJUnit4::class)
class AndroidCharacterPackSinkTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packRoot = File(context.filesDir, "character-packs")

    @Before
    fun clean() = packRoot.deleteRecursively().let {}

    @After
    fun tidy() = packRoot.deleteRecursively().let {}

    private fun chunkOf(bytes: ByteArray) = FolderPush.Chunk(Base64.encode(bytes))

    @Test
    fun writes_pushed_files_to_app_storage() {
        val receiver = CharacterPackReceiver(AndroidCharacterPackSink(context))
        val manifest = """{"name":"bufo"}""".toByteArray()
        val frame = byteArrayOf(0x47, 0x49, 0x46, 0x38) // "GIF8"

        receiver.handle(FolderPush.CharBegin("bufo", (manifest.size + frame.size).toLong()))

        receiver.handle(FolderPush.FileBegin("manifest.json", manifest.size.toLong()))
        receiver.handle(chunkOf(manifest))
        val manifestEnd = receiver.handle(FolderPush.FileEnd)

        // A nested path must create its parent directories.
        receiver.handle(FolderPush.FileBegin("frames/idle.gif", frame.size.toLong()))
        receiver.handle(chunkOf(frame))
        receiver.handle(FolderPush.FileEnd)

        receiver.handle(FolderPush.CharEnd)

        val packDir = File(packRoot, "bufo")
        assertArrayEquals(manifest, File(packDir, "manifest.json").readBytes())
        assertArrayEquals(frame, File(packDir, "frames/idle.gif").readBytes())
        assertEquals(manifest.size.toLong(), manifestEnd.bytes)
    }

    @Test
    fun splits_a_file_across_multiple_chunks() {
        val receiver = CharacterPackReceiver(AndroidCharacterPackSink(context))
        val whole = ByteArray(5000) { (it % 256).toByte() }

        receiver.handle(FolderPush.CharBegin("big", whole.size.toLong()))
        receiver.handle(FolderPush.FileBegin("blob.bin", whole.size.toLong()))
        receiver.handle(chunkOf(whole.copyOfRange(0, 2000)))
        receiver.handle(chunkOf(whole.copyOfRange(2000, whole.size)))
        val end = receiver.handle(FolderPush.FileEnd)
        receiver.handle(FolderPush.CharEnd)

        assertArrayEquals(whole, File(packRoot, "big/blob.bin").readBytes())
        assertEquals(whole.size.toLong(), end.bytes)
    }

    @Test
    fun provider_reads_back_the_pushed_pack() {
        val receiver = CharacterPackReceiver(AndroidCharacterPackSink(context))
        val manifest = """{"name":"bufo","states":{"idle":["idle_0.gif","idle_1.gif"],"busy":"busy.gif"}}"""
            .toByteArray()

        receiver.handle(FolderPush.CharBegin("bufo", manifest.size.toLong()))
        receiver.handle(FolderPush.FileBegin("manifest.json", manifest.size.toLong()))
        receiver.handle(chunkOf(manifest))
        receiver.handle(FolderPush.FileEnd)
        receiver.handle(FolderPush.CharEnd)

        val pack = AndroidCharacterPackProvider(context).activePack()

        assertNotNull("a pushed pack must be discoverable", pack)
        assertEquals("bufo", pack!!.manifest.name)
        assertEquals(listOf("idle_0.gif", "idle_1.gif"), pack.manifest.framesFor("idle"))
        assertEquals(listOf("busy.gif"), pack.manifest.framesFor("busy"))
        assertTrue(File(pack.directoryPath, "manifest.json").exists())
    }

    @Test
    fun provider_returns_null_when_nothing_pushed() {
        assertNull(AndroidCharacterPackProvider(context).activePack())
    }

    @Test
    fun does_not_write_a_file_whose_path_escapes_the_pack() {
        val receiver = CharacterPackReceiver(AndroidCharacterPackSink(context))

        receiver.handle(FolderPush.CharBegin("evil", 4))
        val ack = receiver.handle(FolderPush.FileBegin("../escape.txt", 4))
        receiver.handle(chunkOf(byteArrayOf(1, 2, 3, 4)))
        receiver.handle(FolderPush.FileEnd)
        receiver.handle(FolderPush.CharEnd)

        assertFalse(ack.ok)
        assertFalse("escaping file must not be written", File(packRoot, "escape.txt").exists())
        assertTrue(File(packRoot, "evil").listFiles().isNullOrEmpty())
    }
}
