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
    fun `encodes a chunk ack with the bytes-written counter as n`() {
        val line = ProtocolSerializer.encode(
            OutboundMessage.CommandAck(command = "chunk", ok = true, bytes = 412),
        )

        assertEquals("""{"ack":"chunk","ok":true,"n":412}""", line)
    }

    @Test
    fun `an ack without a byte count omits n`() {
        val line = ProtocolSerializer.encode(
            OutboundMessage.CommandAck(command = "char_begin", ok = true),
        )

        assertEquals("""{"ack":"char_begin","ok":true}""", line)
        assertFalse(line.contains("\"n\""))
    }

    @Test
    fun `output is a single line with no trailing newline`() {
        val line = ProtocolSerializer.encode(
            OutboundMessage.PermissionDecision(promptId = "req_1", choice = PermissionChoice.APPROVE),
        )

        assertFalse("output must not contain a newline", line.contains("\n"))
    }

    @Test
    fun `encodes a full status ack`() {
        val line = ProtocolSerializer.encode(
            OutboundMessage.StatusAck(
                DeviceStatus(
                    name = "Pixel",
                    secure = false,
                    battery = BatteryStatus(percent = 87, milliVolts = 4012, milliAmps = -120, onUsb = true),
                    system = SystemStatus(uptimeSeconds = 8412, freeHeapBytes = 84200),
                    stats = BuddyStats(approvals = 42, denials = 3, velocity = 8, naps = 12, level = 5),
                ),
            ),
        )

        assertEquals(
            """{"ack":"status","ok":true,"data":{"name":"Pixel","sec":false,""" +
                """"bat":{"pct":87,"mV":4012,"mA":-120,"usb":true},""" +
                """"sys":{"up":8412,"heap":84200},"stats":{"appr":42,"deny":3,"vel":8,"nap":12,"lvl":5}}}""",
            line,
        )
    }

    @Test
    fun `a status ack omits absent fields and sub-objects`() {
        val line = ProtocolSerializer.encode(
            OutboundMessage.StatusAck(
                DeviceStatus(battery = BatteryStatus(percent = 50, onUsb = false)),
            ),
        )

        assertEquals(
            """{"ack":"status","ok":true,"data":{"bat":{"pct":50,"usb":false}}}""",
            line,
        )
    }

    @Test
    fun `an empty status ack has an empty data object`() {
        val line = ProtocolSerializer.encode(OutboundMessage.StatusAck(DeviceStatus()))

        assertEquals("""{"ack":"status","ok":true,"data":{}}""", line)
    }

    @Test
    fun `escapes special characters in error text`() {
        val line = ProtocolSerializer.encode(
            OutboundMessage.CommandAck(command = "name", ok = false, error = """bad "value""""),
        )

        assertEquals("""{"ack":"name","ok":false,"error":"bad \"value\""}""", line)
    }
}
