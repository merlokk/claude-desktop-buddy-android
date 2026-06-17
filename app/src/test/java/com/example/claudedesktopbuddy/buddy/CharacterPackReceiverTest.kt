package com.example.claudedesktopbuddy.buddy

import com.example.claudedesktopbuddy.protocol.InboundMessage
import com.example.claudedesktopbuddy.protocol.OutboundMessage
import kotlin.io.encoding.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterPackReceiverTest {

    /** Records the sink calls so a test can assert what was written and in which order. */
    private class RecordingSink : CharacterPackSink {
        data class WrittenFile(val path: String, val size: Long, val bytes: ByteArray)

        val events = mutableListOf<String>()
        val files = mutableListOf<WrittenFile>()

        private var openPath: String? = null
        private var openSize: Long = 0
        private var buffer = mutableListOf<Byte>()

        override fun beginPack(name: String, totalBytes: Long) {
            events += "beginPack($name,$totalBytes)"
        }

        override fun beginFile(path: String, sizeBytes: Long) {
            events += "beginFile($path,$sizeBytes)"
            openPath = path
            openSize = sizeBytes
            buffer = mutableListOf()
        }

        override fun write(bytes: ByteArray) {
            buffer.addAll(bytes.toList())
        }

        override fun endFile() {
            events += "endFile"
            files += WrittenFile(openPath!!, openSize, buffer.toByteArray())
            openPath = null
        }

        override fun endPack() {
            events += "endPack"
        }
    }

    private fun chunkOf(bytes: ByteArray) = InboundMessage.FolderPush.Chunk(Base64.encode(bytes))

    @Test
    fun `acks each step and writes the decoded file to the sink`() {
        val sink = RecordingSink()
        val receiver = CharacterPackReceiver(sink)
        val payload = "hello world".toByteArray()

        val acks = listOf(
            receiver.handle(InboundMessage.FolderPush.CharBegin("bufo", payload.size.toLong())),
            receiver.handle(InboundMessage.FolderPush.FileBegin("greeting.txt", payload.size.toLong())),
            receiver.handle(chunkOf(payload)),
            receiver.handle(InboundMessage.FolderPush.FileEnd),
            receiver.handle(InboundMessage.FolderPush.CharEnd),
        )

        assertEquals(
            listOf("beginPack(bufo,11)", "beginFile(greeting.txt,11)", "endFile", "endPack"),
            sink.events,
        )
        assertEquals(1, sink.files.size)
        assertEquals("greeting.txt", sink.files.single().path)
        assertArrayEquals(payload, sink.files.single().bytes)
        assertTrue(acks.all { it.ok })
        assertEquals(listOf("char_begin", "file", "chunk", "file_end", "char_end"), acks.map { it.command })
    }

    @Test
    fun `chunk ack reports cumulative bytes and file_end reports the final size`() {
        val receiver = CharacterPackReceiver(RecordingSink())
        receiver.handle(InboundMessage.FolderPush.CharBegin("p", 5))
        receiver.handle(InboundMessage.FolderPush.FileBegin("a.bin", 5))

        val firstChunk = receiver.handle(chunkOf(byteArrayOf(1, 2, 3)))
        val secondChunk = receiver.handle(chunkOf(byteArrayOf(4, 5)))
        val fileEnd = receiver.handle(InboundMessage.FolderPush.FileEnd)

        assertEquals(3L, firstChunk.bytes)
        assertEquals(5L, secondChunk.bytes)
        assertEquals(5L, fileEnd.bytes)
    }

    @Test
    fun `byte counters reset between files`() {
        val receiver = CharacterPackReceiver(RecordingSink())
        receiver.handle(InboundMessage.FolderPush.CharBegin("p", 6))

        receiver.handle(InboundMessage.FolderPush.FileBegin("a.bin", 3))
        receiver.handle(chunkOf(byteArrayOf(1, 2, 3)))
        receiver.handle(InboundMessage.FolderPush.FileEnd)

        receiver.handle(InboundMessage.FolderPush.FileBegin("b.bin", 1))
        val secondFileChunk = receiver.handle(chunkOf(byteArrayOf(9)))

        assertEquals(1L, secondFileChunk.bytes)
    }

    @Test
    fun `rejects an unsafe path and drops its chunks`() {
        val sink = RecordingSink()
        val receiver = CharacterPackReceiver(sink)
        receiver.handle(InboundMessage.FolderPush.CharBegin("p", 10))

        val fileAck = receiver.handle(InboundMessage.FolderPush.FileBegin("../escape.txt", 4))
        val chunkAck = receiver.handle(chunkOf(byteArrayOf(1, 2, 3, 4)))
        val endAck = receiver.handle(InboundMessage.FolderPush.FileEnd)

        assertFalse(fileAck.ok)
        assertTrue(chunkAck.ok) // We still ack so the desktop's sequential stream proceeds.
        assertEquals(0L, chunkAck.bytes) // ...but nothing was written.
        assertEquals(0L, endAck.bytes)
        assertTrue(sink.files.isEmpty())
        assertFalse(sink.events.any { it.startsWith("beginFile") })
    }

    @Test
    fun `a malformed base64 chunk is nacked without writing`() {
        val sink = RecordingSink()
        val receiver = CharacterPackReceiver(sink)
        receiver.handle(InboundMessage.FolderPush.CharBegin("p", 4))
        receiver.handle(InboundMessage.FolderPush.FileBegin("a.bin", 4))

        val chunkAck = receiver.handle(InboundMessage.FolderPush.Chunk("not valid base64 !!!"))

        assertFalse(chunkAck.ok)
        assertTrue(sink.files.isEmpty() || sink.files.all { it.bytes.isEmpty() })
    }

    @Test
    fun `isSafePackPath rejects escaping and absolute paths`() {
        assertTrue(isSafePackPath("manifest.json"))
        assertTrue(isSafePackPath("frames/idle/00.png"))
        assertFalse(isSafePackPath(""))
        assertFalse(isSafePackPath("/etc/passwd"))
        assertFalse(isSafePackPath("""C:\Windows\system32"""))
        assertFalse(isSafePackPath("../secrets"))
        assertFalse(isSafePackPath("a/../../b"))
    }
}
