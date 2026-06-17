package com.example.claudedesktopbuddy.transport

import java.io.ByteArrayOutputStream

private const val NEWLINE: Byte = '\n'.code.toByte()
private const val CARRIAGE_RETURN = '\r'

/**
 * Reassembles complete, newline-delimited protocol lines from arbitrarily chunked byte input.
 *
 * BLE notifications arrive in chunks that do not respect line boundaries (and may even split a
 * single multibyte UTF-8 character), so bytes are buffered until a `\n` (`0x0A`) is seen. Each
 * [append] returns the lines completed by the new data, in order; any trailing partial line is
 * kept for the next call.
 *
 * Splitting happens on the raw `0x0A` byte before decoding: that byte never occurs inside a
 * multibyte UTF-8 sequence, so each completed line decodes as valid UTF-8 even when a character
 * was split across two chunks. Terminators are stripped (including a trailing `\r` for CRLF), and
 * blank lines are dropped since they carry no protocol message.
 *
 * Not thread-safe: feed one transport's bytes from a single reader.
 */
class LineAssembler {

    private val pending = ByteArrayOutputStream()

    fun append(chunk: ByteArray): List<String> {
        val lines = mutableListOf<String>()
        for (byte in chunk) {
            if (byte == NEWLINE) {
                decodeLine(pending.toByteArray())?.let(lines::add)
                pending.reset()
            } else {
                pending.write(byte.toInt())
            }
        }
        return lines
    }

    /** Decodes one line's bytes, stripping a trailing CR; returns null for a blank line. */
    private fun decodeLine(bytes: ByteArray): String? {
        val text = bytes.toString(Charsets.UTF_8).removeSuffix(CARRIAGE_RETURN.toString())
        return text.ifBlank { null }
    }
}

/**
 * Encodes one complete protocol line for transmission: UTF-8 bytes followed by a single `\n`
 * terminator. [line] must not already contain a terminator (the serializer never adds one).
 */
fun encodeLine(line: String): ByteArray = (line + "\n").toByteArray(Charsets.UTF_8)
