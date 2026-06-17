package com.example.claudedesktopbuddy.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LineFramingTest {

    private fun bytes(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

    @Test
    fun `returns a complete line delivered in one chunk`() {
        val assembler = LineAssembler()

        assertEquals(listOf("""{"a":1}"""), assembler.append(bytes("{\"a\":1}\n")))
    }

    @Test
    fun `strips the newline terminator`() {
        val assembler = LineAssembler()

        val line = assembler.append(bytes("hello\n")).single()

        assertEquals("hello", line)
    }

    @Test
    fun `returns nothing until a line is terminated`() {
        val assembler = LineAssembler()

        assertEquals(emptyList<String>(), assembler.append(bytes("{\"a\":")))
    }

    @Test
    fun `reassembles a line split across chunks`() {
        val assembler = LineAssembler()

        assertEquals(emptyList<String>(), assembler.append(bytes("{\"a\":")))
        assertEquals(listOf("""{"a":1}"""), assembler.append(bytes("1}\n")))
    }

    @Test
    fun `splits multiple lines from one chunk in order`() {
        val assembler = LineAssembler()

        val lines = assembler.append(bytes("one\ntwo\nthree\n"))

        assertEquals(listOf("one", "two", "three"), lines)
    }

    @Test
    fun `keeps a trailing partial line for the next chunk`() {
        val assembler = LineAssembler()

        assertEquals(listOf("one"), assembler.append(bytes("one\ntw")))
        assertEquals(listOf("two"), assembler.append(bytes("o\n")))
    }

    @Test
    fun `strips a trailing carriage return for CRLF input`() {
        val assembler = LineAssembler()

        assertEquals(listOf("line"), assembler.append(bytes("line\r\n")))
    }

    @Test
    fun `drops blank lines`() {
        val assembler = LineAssembler()

        assertEquals(listOf("a", "b"), assembler.append(bytes("a\n\n\nb\n")))
    }

    @Test
    fun `an empty chunk yields no lines`() {
        val assembler = LineAssembler()

        assertEquals(emptyList<String>(), assembler.append(ByteArray(0)))
    }

    @Test
    fun `decodes a multibyte character split across chunks`() {
        val assembler = LineAssembler()
        val full = bytes("café\n") // 'é' is 0xC3 0xA9
        val splitAt = full.size - 2 // cut between the two bytes of 'é'

        assertEquals(emptyList<String>(), assembler.append(full.copyOfRange(0, splitAt)))
        assertEquals(listOf("café"), assembler.append(full.copyOfRange(splitAt, full.size)))
    }

    @Test
    fun `encodeLine appends a single newline and encodes as UTF-8`() {
        assertArrayEquals(bytes("café\n"), encodeLine("café"))
    }

    @Test
    fun `encodeLine output round-trips through the assembler`() {
        val assembler = LineAssembler()
        val original = """{"cmd":"permission","id":"req_1","decision":"once"}"""

        assertEquals(listOf(original), assembler.append(encodeLine(original)))
    }
}
