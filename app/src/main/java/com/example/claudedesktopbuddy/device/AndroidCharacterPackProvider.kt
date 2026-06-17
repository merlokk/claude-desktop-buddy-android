package com.example.claudedesktopbuddy.device

import android.content.Context
import android.util.Log
import com.example.claudedesktopbuddy.buddy.CharacterPack
import com.example.claudedesktopbuddy.buddy.CharacterPackProvider
import com.example.claudedesktopbuddy.protocol.CharacterManifestParser
import java.io.File

/**
 * Reads the most recently pushed character pack from app storage. A pack is a sub-directory of
 * `filesDir/character-packs` that contains a `manifest.json`; the newest such directory (by last
 * modification) wins, so a freshly dropped folder replaces the previous one.
 *
 * Returns null when nothing has been pushed yet or the manifest can't be read, so the UI simply
 * shows no avatar.
 */
class AndroidCharacterPackProvider(context: Context) : CharacterPackProvider {

    private val root = File(context.applicationContext.filesDir, CHARACTER_PACKS_DIR)

    override fun activePack(): CharacterPack? {
        val newest = root.listFiles { f -> f.isDirectory }
            ?.filter { File(it, MANIFEST).isFile }
            ?.maxByOrNull { File(it, MANIFEST).lastModified() }
            ?: return null

        return try {
            val manifest = CharacterManifestParser.parse(File(newest, MANIFEST).readText())
            CharacterPack(manifest = manifest, directoryPath = newest.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read manifest in ${newest.name}", e)
            null
        }
    }

    private companion object {
        const val TAG = "CharacterPackProvider"
        const val MANIFEST = "manifest.json"
    }
}
