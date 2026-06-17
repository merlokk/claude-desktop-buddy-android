package com.example.claudedesktopbuddy.buddy

import com.example.claudedesktopbuddy.protocol.CharacterManifest

/**
 * A character pack the desktop has pushed and that has finished downloading: its parsed [manifest]
 * and the absolute [directoryPath] its files were written to. The UI resolves a frame filename to a
 * file under [directoryPath] for rendering. A plain path string keeps this framework-free.
 */
data class CharacterPack(
    val manifest: CharacterManifest,
    val directoryPath: String,
)

/**
 * Supplies the character pack to display, if any has been pushed. Implemented in the Android layer
 * (it reads app storage); the orchestrator stays framework-free and just exposes what it returns.
 */
fun interface CharacterPackProvider {

    /** The pack to show now, or null when none has been pushed yet (or it can't be read). */
    fun activePack(): CharacterPack?

    companion object {
        /** A provider that never has a pack — used by default and in tests. */
        val None = CharacterPackProvider { null }
    }
}
