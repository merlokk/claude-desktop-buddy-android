package com.example.claudedesktopbuddy.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProtocolSerializerTest {

    @Test
    fun `encodes an approve decision as once`() {
        val line = ProtocolSerializer.encode(
            OutboundMessage.PermissionDecision(promptId = "req_abc123", choice = PermissionChoice.APPROVE),
        )

        assertEquals("""{"cmd":"permission","id":"req_abc123","decision":"once"}""", line)
    }

    @Test
    fun `encodes a deny decision as deny`() {
        val line = ProtocolSerializer.encode(
            OutboundMessage.PermissionDecision(promptId = "req_abc123", choice = PermissionChoice.DENY),
        )

        assertEquals("""{"cmd":"permission","id":"req_abc123","decision":"deny"}""", line)
    }

    @Test
    fun `encodes a successful command ack`() {
        val line = ProtocolSerializer.encode(
            OutboundMessage.CommandAck(command = "status", ok = true),
        )

        assertEquals("""{"ack":"status","ok":true}""", line)
    }

    @Test
    fun `a successful ack omits the error field`() {
        val line = ProtocolSerializer.encode(
            OutboundMessage.CommandAck(command = "name", ok = true),
        )

        assertFalse(line.contains("error"))
    }

    @Test
    fun `encodes a failed command ack with an error message`() {
        val line = ProtocolSerializer.encode(
            OutboundMessage.CommandAck(command = "unpair", ok = false, error = "not paired"),
        )

        assertEquals("""{"ack":"unpair","ok":false,"error":"not paired"}""", line)
    }

    @Test
    fun `output is a single line with no trailing newline`() {
        val line = ProtocolSerializer.encode(
            OutboundMessage.PermissionDecision(promptId = "req_1", choice = PermissionChoice.APPROVE),
        )

        assertFalse("output must not contain a newline", line.contains("\n"))
    }

    @Test
    fun `escapes special characters in error text`() {
        val line = ProtocolSerializer.encode(
            OutboundMessage.CommandAck(command = "name", ok = false, error = """bad "value""""),
        )

        assertEquals("""{"ack":"name","ok":false,"error":"bad \"value\""}""", line)
    }
}
