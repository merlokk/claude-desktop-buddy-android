package com.example.claudedesktopbuddy.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Serializes an [OutboundMessage] into a single line of the Nordic UART JSON protocol: one
 * compact JSON object with no trailing newline. The transport is responsible for appending `\n`.
 */
object ProtocolSerializer {

    fun encode(message: OutboundMessage): String = Json.encodeToString(toJson(message))

    private fun toJson(message: OutboundMessage): JsonObject = when (message) {
        is OutboundMessage.PermissionDecision -> buildJsonObject {
            put("cmd", "permission")
            put("id", message.promptId)
            put("decision", message.choice.wireValue)
        }

        is OutboundMessage.CommandAck -> buildJsonObject {
            put("ack", message.command)
            put("ok", message.ok)
            message.error?.let { put("error", it) }
        }

        is OutboundMessage.StatusAck -> buildJsonObject {
            put("ack", "status")
            put("ok", true)
            put("data", encodeStatusData(message.status))
        }
    }

    private fun encodeStatusData(status: DeviceStatus): JsonObject = buildJsonObject {
        status.name?.let { put("name", it) }
        status.secure?.let { put("sec", it) }
        status.battery?.let { battery ->
            put("bat", buildJsonObject {
                battery.percent?.let { put("pct", it) }
                battery.milliVolts?.let { put("mV", it) }
                battery.milliAmps?.let { put("mA", it) }
                battery.onUsb?.let { put("usb", it) }
            })
        }
        status.system?.let { system ->
            put("sys", buildJsonObject {
                system.uptimeSeconds?.let { put("up", it) }
                system.freeHeapBytes?.let { put("heap", it) }
            })
        }
        status.stats?.let { stats ->
            put("stats", buildJsonObject {
                put("appr", stats.approvals)
                put("deny", stats.denials)
            })
        }
    }
}
