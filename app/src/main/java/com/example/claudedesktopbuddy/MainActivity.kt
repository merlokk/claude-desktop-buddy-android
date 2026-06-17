package com.example.claudedesktopbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.claudedesktopbuddy.buddy.BuddyAndroidViewModel
import com.example.claudedesktopbuddy.ui.BuddyScreen
import com.example.claudedesktopbuddy.ui.LogsScreen
import com.example.claudedesktopbuddy.ui.theme.ClaudeDesktopBuddyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClaudeDesktopBuddyTheme {
                ClaudeDesktopBuddyApp()
            }
        }
    }
}

@Composable
fun ClaudeDesktopBuddyApp(
    viewModel: BuddyAndroidViewModel = viewModel(factory = BuddyAndroidViewModel.Factory),
) {
    var current by rememberSaveable { mutableStateOf(AppDestination.BUDDY) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(painterResource(destination.icon), contentDescription = null)
                    },
                    label = { Text(stringResource(destination.label)) },
                    selected = destination == current,
                    onClick = { current = destination },
                )
            }
        },
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (current) {
                AppDestination.BUDDY -> BuddyScreen(
                    state = state,
                    onApprove = viewModel::approve,
                    onDeny = viewModel::deny,
                    modifier = Modifier.padding(innerPadding),
                )

                AppDestination.LOGS -> LogsScreen(
                    log = log,
                    onLoggingEnabledChange = viewModel::setLoggingEnabled,
                    onClear = viewModel::clearLog,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

enum class AppDestination(
    val label: Int,
    val icon: Int,
) {
    BUDDY(R.string.nav_buddy, R.drawable.ic_home),
    LOGS(R.string.nav_logs, R.drawable.ic_logs),
}
