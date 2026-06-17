package com.example.claudedesktopbuddy.buddy

import com.example.claudedesktopbuddy.protocol.CommandVerbs
import com.example.claudedesktopbuddy.protocol.InboundMessage
import com.example.claudedesktopbuddy.protocol.OutboundMessage
import kotlin.io.encoding.Base64

/**
 * Drives the desktop's folder-push stream: folds each [InboundMessage.FolderPush] step into the
 * [sink] and returns the ack the desktop is waiting for before it sends the next step.
 *
 * Pure Kotlin and single-threaded — the desktop never sends the next step until it has our ack, so
 * the running counters need no synchronization. The `n` field of chunk/file_end acks reports the
 * bytes written for the current file (the protocol's `bytes_written_so_far` / `final_size`).
 *
 * Untrusted input: the desktop sends whatever filenames are in the dropped folder, so a file whose
 * path escapes the pack (absolute, a drive letter, or containing `..`) is rejected with `ok:false`
 * and its chunks are dropped rather than written.
 */
class CharacterPackReceiver(private val sink: CharacterPackSink) {

    private var fileAccepted = false
    private var currentFileBytes = 0L

    /** Handles one folder-push step and returns the ack to send back to the desktop. */
    fun handle(message: InboundMessage.FolderPush): OutboundMessage.CommandAck = when (message) {
        is InboundMessage.FolderPush.CharBegin -> {
            sink.beginPack(message.name, message.totalBytes)
            ok(CommandVerbs.CHAR_BEGIN)
        }

        is InboundMessage.FolderPush.FileBegin -> beginFile(message)
        is InboundMessage.FolderPush.Chunk -> writeChunk(message)
        InboundMessage.FolderPush.FileEnd -> endFile()

        InboundMessage.FolderPush.CharEnd -> {
            sink.endPack()
            ok(CommandVerbs.CHAR_END)
        }
    }

    private fun beginFile(message: InboundMessage.FolderPush.FileBegin): OutboundMessage.CommandAck {
        currentFileBytes = 0L
        return if (isSafePackPath(message.path)) {
            fileAccepted = true
            sink.beginFile(message.path, message.sizeBytes)
            ok(CommandVerbs.FILE)
        } else {
            fileAccepted = false
            OutboundMessage.CommandAck(command = CommandVerbs.FILE, ok = false, error = "unsafe path")
        }
    }

    private fun writeChunk(message: InboundMessage.FolderPush.Chunk): OutboundMessage.CommandAck {
        val bytes = try {
            Base64.decode(message.dataBase64)
        } catch (e: IllegalArgumentException) {
            return OutboundMessage.CommandAck(command = CommandVerbs.CHUNK, ok = false, error = "bad base64")
        }
        if (fileAccepted) {
            sink.write(bytes)
            currentFileBytes += bytes.size
        }
        return OutboundMessage.CommandAck(command = CommandVerbs.CHUNK, ok = true, bytes = currentFileBytes)
    }

    private fun endFile(): OutboundMessage.CommandAck {
        val finalSize = currentFileBytes
        if (fileAccepted) sink.endFile()
        fileAccepted = false
        currentFileBytes = 0L
        return OutboundMessage.CommandAck(command = CommandVerbs.FILE_END, ok = true, bytes = finalSize)
    }

    private fun ok(command: String) = OutboundMessage.CommandAck(command = command, ok = true)
}

/**
 * True when [path] is safe to write under the pack root: a non-blank, relative path with no `..`
 * segment and no drive letter. Rejecting these keeps a malicious or careless folder from writing
 * outside the pack directory.
 */
internal fun isSafePackPath(path: String): Boolean {
    if (path.isBlank()) return false
    if (path.startsWith("/") || path.startsWith("\\")) return false
    if (path.length >= 2 && path[1] == ':') return false // Windows drive letter, e.g. C:
    return path.split('/', '\\').none { it == ".." }
}
