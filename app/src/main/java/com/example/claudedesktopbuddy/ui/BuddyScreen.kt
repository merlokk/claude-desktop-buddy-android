package com.example.claudedesktopbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.claudedesktopbuddy.R
import com.example.claudedesktopbuddy.buddy.BuddyActivity
import com.example.claudedesktopbuddy.buddy.BuddyState
import com.example.claudedesktopbuddy.protocol.PermissionPrompt
import com.example.claudedesktopbuddy.ui.theme.ClaudeDesktopBuddyTheme

/**
 * The home screen: shows what Claude is doing and, when a prompt is pending, the question plus
 * approve/deny buttons. Stateless — the host collects [state] and supplies the callbacks.
 */
@Composable
fun BuddyScreen(
    state: BuddyState,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(state.activity.labelRes),
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = state.statusMessage ?: stringResource(R.string.buddy_no_status),
            style = MaterialTheme.typography.bodyLarge,
        )

        state.pendingPrompt?.let { prompt ->
            PermissionPromptCard(prompt = prompt, onApprove = onApprove, onDeny = onDeny)
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.buddy_tokens, state.tokens, state.tokensToday),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (state.recentEntries.isNotEmpty()) {
            RecentEntries(entries = state.recentEntries)
        }
    }
}

@Composable
private fun PermissionPromptCard(
    prompt: PermissionPrompt,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.prompt_question, prompt.tool),
                style = MaterialTheme.typography.titleLarge,
            )
            prompt.hint?.let { hint ->
                Text(text = hint, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDeny, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_deny))
                }
                Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_approve))
                }
            }
        }
    }
}

@Composable
private fun RecentEntries(entries: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.buddy_recent_title),
            style = MaterialTheme.typography.titleSmall,
        )
        entries.forEach { entry ->
            Text(text = entry, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val BuddyActivity.labelRes: Int
    get() = when (this) {
        BuddyActivity.IDLE -> R.string.activity_idle
        BuddyActivity.BUSY -> R.string.activity_busy
        BuddyActivity.AWAITING_APPROVAL -> R.string.activity_awaiting_approval
    }

@Preview(showBackground = true)
@Composable
private fun BuddyScreenBusyPreview() {
    ClaudeDesktopBuddyTheme {
        BuddyScreen(
            state = BuddyState(running = 1, statusMessage = "Running tests…", tokens = 184502, tokensToday = 31200),
            onApprove = {},
            onDeny = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BuddyScreenPromptPreview() {
    ClaudeDesktopBuddyTheme {
        BuddyScreen(
            state = BuddyState(
                running = 1,
                statusMessage = "approve: Bash",
                pendingPrompt = PermissionPrompt(id = "req_1", tool = "Bash", hint = "rm -rf /tmp/foo"),
            ),
            onApprove = {},
            onDeny = {},
        )
    }
}
