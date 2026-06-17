package com.example.claudedesktopbuddy.device

import android.content.Context
import android.util.Log
import com.example.claudedesktopbuddy.buddy.CharacterPackSink
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream

/**
 * Persists a pushed folder under the app's private storage: `filesDir/character-packs/<pack>/<path>`.
 *
 * The pack name is reduced to a safe directory name and each file's path is resolved under the pack
 * directory and re-checked to be inside it (defence in depth — [CharacterPackReceiver] already
 * rejects escaping paths). One file is open at a time, matching the sequential protocol.
 */
class AndroidCharacterPackSink(context: Context) : CharacterPackSink {

    private val root = File(context.applicationContext.filesDir, "character-packs")

    private var packDir: File? = null
    private var currentFile: OutputStream? = null

    override fun beginPack(name: String, totalBytes: Long) {
        val safeName = name.ifBlank { "pack" }.replace(UNSAFE_NAME_CHARS, "_")
        packDir = File(root, safeName).apply { mkdirs() }
    }

    override fun beginFile(path: String, sizeBytes: Long) {
        val dir = packDir ?: return
        val target = File(dir, path).canonicalFile
        if (!target.path.startsWith(dir.canonicalFile.path + File.separator)) {
            Log.w(TAG, "Refusing file outside the pack directory: $path")
            return
        }
        target.parentFile?.mkdirs()
        currentFile = BufferedOutputStream(target.outputStream())
    }

    override fun write(bytes: ByteArray) {
        currentFile?.write(bytes)
    }

    override fun endFile() {
        currentFile?.use { it.flush() }
        currentFile = null
    }

    override fun endPack() {
        packDir = null
    }

    private companion object {
        const val TAG = "CharacterPackSink"
        val UNSAFE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}
