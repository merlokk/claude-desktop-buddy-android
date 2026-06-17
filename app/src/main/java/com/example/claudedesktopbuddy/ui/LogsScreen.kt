package com.example.claudedesktopbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.claudedesktopbuddy.R
import com.example.claudedesktopbuddy.log.ExchangeLog
import com.example.claudedesktopbuddy.log.LogDirection
import com.example.claudedesktopbuddy.log.LogEntry
import com.example.claudedesktopbuddy.ui.theme.ClaudeDesktopBuddyTheme

/**
 * The logs screen: a switch to enable/disable raw-traffic logging (off by default), a clear
 * action, and one JSON line per row. Stateless — the host collects [log] and supplies callbacks.
 */
@Composable
fun LogsScreen(
    log: ExchangeLog,
    onLoggingEnabledChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LogsToolbar(
            enabled = log.enabled,
            onLoggingEnabledChange = onLoggingEnabledChange,
            onClear = onClear,
        )
        HorizontalDivider()

        when {
            !log.enabled && log.entries.isEmpty() -> CenteredHint(stringResource(R.string.logs_disabled_hint))
            log.entries.isEmpty() -> CenteredHint(stringResource(R.string.logs_empty))
            else -> LogList(entries = log.entries)
        }
    }
}

@Composable
private fun LogsToolbar(
    enabled: Boolean,
    onLoggingEnabledChange: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.logs_enable), modifier = Modifier.weight(1f))
        TextButton(onClick = onClear) { Text(stringResource(R.string.logs_clear)) }
        Switch(checked = enabled, onCheckedChange = onLoggingEnabledChange)
    }
}

@Composable
private fun LogList(entries: List<LogEntry>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(entries) { entry -> LogRow(entry) }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val arrow = when (entry.direction) {
        LogDirection.INCOMING -> stringResource(R.string.logs_incoming_arrow)
        LogDirection.OUTGOING -> stringResource(R.string.logs_outgoing_arrow)
    }
    Text(
        text = "$arrow ${entry.line}",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun CenteredHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun LogsScreenPreview() {
    ClaudeDesktopBuddyTheme {
        LogsScreen(
            log = ExchangeLog(
                enabled = true,
                entries = listOf(
                    LogEntry(LogDirection.INCOMING, """{"running":1,"msg":"thinking"}"""),
                    LogEntry(LogDirection.OUTGOING, """{"cmd":"permission","id":"req_1","decision":"once"}"""),
                ),
            ),
            onLoggingEnabledChange = {},
            onClear = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LogsScreenDisabledPreview() {
    ClaudeDesktopBuddyTheme {
        LogsScreen(log = ExchangeLog(), onLoggingEnabledChange = {}, onClear = {})
    }
}
