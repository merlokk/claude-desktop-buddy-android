package com.example.claudedesktopbuddy.buddy

import com.example.claudedesktopbuddy.protocol.CharacterManifest

/**
 * The animation states a character pack can define (the upstream firmware's seven states). Each maps
 * to a key in the manifest's `states` object via [manifestKey].
 *
 * We only ever drive four of them from the buddy state (see [CharacterPresenter.stateFor]); the
 * remaining three need signals this port does not track (`celebrate` a level-up, `dizzy` a shake,
 * `heart` a quick approval) and are listed for completeness / future use.
 */
enum class CharacterState(val manifestKey: String) {
    SLEEP("sleep"),
    IDLE("idle"),
    BUSY("busy"),
    ATTENTION("attention"),
    CELEBRATE("celebrate"),
    DIZZY("dizzy"),
    HEART("heart"),
}

/**
 * Pure mapping from the buddy domain to a character animation: which [CharacterState] to show and
 * which GIF frame to play. Framework-free so the UI just renders what this decides.
 */
object CharacterPresenter {

    /**
     * The animation state for the current buddy [state]. A dropped connection sleeps the character
     * regardless of the last-known activity; otherwise a pending prompt, then active work, then idle.
     */
    fun stateFor(state: BuddyState): CharacterState = when {
        !state.isConnected -> CharacterState.SLEEP
        state.activity == BuddyActivity.AWAITING_APPROVAL -> CharacterState.ATTENTION
        state.activity == BuddyActivity.BUSY -> CharacterState.BUSY
        else -> CharacterState.IDLE
    }

    /**
     * The GIF filenames to play for [state], falling back to the `idle` frames when the pack defines
     * no animation for that state, and to empty when it defines neither.
     */
    fun framesFor(manifest: CharacterManifest, state: CharacterState): List<String> =
        manifest.framesFor(state.manifestKey)
            .ifEmpty { manifest.framesFor(CharacterState.IDLE.manifestKey) }

    /**
     * Picks the frame for the given [rotation] of a carousel — arrays advance one step per animation
     * loop, wrapping around. Returns null when there are no frames. [rotation] may be any integer.
     */
    fun frameAt(frames: List<String>, rotation: Int): String? =
        if (frames.isEmpty()) null else frames[rotation.mod(frames.size)]
}
