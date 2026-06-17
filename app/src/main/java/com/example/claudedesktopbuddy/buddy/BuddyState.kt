package com.example.claudedesktopbuddy.buddy

import com.example.claudedesktopbuddy.protocol.InboundMessage
import com.example.claudedesktopbuddy.protocol.OutboundMessage
import com.example.claudedesktopbuddy.protocol.PermissionChoice
import com.example.claudedesktopbuddy.protocol.PermissionPrompt

/** What Claude is currently doing, as shown on the buddy screen. */
enum class BuddyActivity {
    /** No sessions are running and nothing needs the user. */
    IDLE,

    /** Claude is working on at least one session. */
    BUSY,

    /** A permission prompt is waiting for the user to approve or deny. */
    AWAITING_APPROVAL,
}

/**
 * Immutable snapshot of the buddy domain state. Pure Kotlin, free of Android and transport types:
 * inbound messages are folded in via [reduce], and a pending prompt is resolved via [answer].
 *
 * [activity] is derived from the stored fields rather than stored separately, so there is a single
 * source of truth — answering a prompt simply clears it and the activity recomputes from the last
 * known [running] count.
 */
data class BuddyState(
    val running: Int = 0,
    val total: Int = 0,
    val waiting: Int = 0,
    val statusMessage: String? = null,
    val recentEntries: List<String> = emptyList(),
    val tokens: Long = 0,
    val tokensToday: Long = 0,
    val pendingPrompt: PermissionPrompt? = null,
    val approvals: Int = 0,
    val denials: Int = 0,
) {

    val activity: BuddyActivity
        get() = when {
            pendingPrompt != null -> BuddyActivity.AWAITING_APPROVAL
            running > 0 -> BuddyActivity.BUSY
            else -> BuddyActivity.IDLE
        }

    /**
     * Folds one inbound message into a new state. Only [InboundMessage.Snapshot] carries buddy
     * state; other messages (time sync, commands, turn events, unknown) leave the state unchanged.
     */
    fun reduce(message: InboundMessage): BuddyState = when (message) {
        is InboundMessage.Snapshot -> copy(
            running = message.running,
            total = message.total,
            waiting = message.waiting,
            statusMessage = message.message,
            recentEntries = message.entries,
            tokens = message.tokens,
            tokensToday = message.tokensToday,
            pendingPrompt = message.prompt,
        )

        is InboundMessage.TimeSync,
        is InboundMessage.Command,
        is InboundMessage.TurnEvent,
        is InboundMessage.Unknown,
        -> this
    }

    /**
     * Resolves the pending prompt with the user's [choice]: returns the decision to send and the
     * new state with the prompt cleared, or null (no change) when no prompt is pending.
     */
    fun answer(choice: PermissionChoice): PromptAnswer? {
        val prompt = pendingPrompt ?: return null
        val cleared = copy(
            pendingPrompt = null,
            approvals = approvals + if (choice == PermissionChoice.APPROVE) 1 else 0,
            denials = denials + if (choice == PermissionChoice.DENY) 1 else 0,
        )
        return PromptAnswer(
            state = cleared,
            decision = OutboundMessage.PermissionDecision(promptId = prompt.id, choice = choice),
        )
    }
}

/** The outcome of [BuddyState.answer]: the next state plus the decision to transmit. */
data class PromptAnswer(
    val state: BuddyState,
    val decision: OutboundMessage.PermissionDecision,
)
