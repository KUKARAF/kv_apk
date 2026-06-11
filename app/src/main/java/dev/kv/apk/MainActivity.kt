package dev.kv.apk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kv.apk.data.ApprovalItem
import dev.kv.apk.data.Prefs
import dev.kv.apk.data.buildApi
import dev.kv.apk.ui.ApprovalsScreen
import dev.kv.apk.ui.KeysScreen
import dev.kv.apk.ui.KvEntriesScreen
import dev.kv.apk.ui.QrScannerScreen
import dev.kv.apk.ui.SetupScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

private enum class Screen { SETUP, SCANNING, MAIN }

class MainActivity : ComponentActivity() {

    private var deepLinkSessionRequestId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MaterialTheme {
                val prefs = remember { Prefs(applicationContext) }
                var screen by remember {
                    mutableStateOf(if (prefs.hasCredentials()) Screen.MAIN else Screen.SETUP)
                }

                when (screen) {
                    Screen.SETUP -> SetupScreen(
                        onScanQr = { screen = Screen.SCANNING },
                        onSaved = { token ->
                            prefs.token = token
                            screen = Screen.MAIN
                        },
                    )
                    Screen.SCANNING -> QrScannerScreen(
                        onScanned = { token ->
                            prefs.token = token.trim()
                            screen = Screen.MAIN
                        },
                        onCancel = { screen = Screen.SETUP },
                    )
                    Screen.MAIN -> MainScreen(
                        prefs = prefs,
                        deepLinkSessionRequestId = deepLinkSessionRequestId,
                        onLogout = {
                            // Revoke session key first, then clear local token
                            try {
                                runBlocking {
                                    buildApi(prefs.token).revokeSessionKey()
                                }
                            } catch (_: Exception) {
                                // Ignore errors during logout - proceed to clear local state
                            }
                            prefs.clear()
                            screen = Screen.SETUP
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme == "kvapp" && data.host == "session-request") {
            deepLinkSessionRequestId = data.getQueryParameter("id")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    prefs: Prefs,
    onLogout: () -> Unit,
    deepLinkSessionRequestId: String? = null,
) {
    val api = remember { buildApi(prefs.token) }

    var approvals by remember { mutableStateOf<List<ApprovalItem>>(emptyList()) }
    var loadingApprovals by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }

    // Keep approvals in sync even when on other tabs
    LaunchedEffect(refreshTick) {
        loadingApprovals = true
        try {
            approvals = api.listApprovals()
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) onLogout()
        } catch (_: Exception) { }
        finally { loadingApprovals = false }
    }

    // Auto-refresh every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            refreshTick++
        }
    }

    var selectedTabIndex by remember {
        mutableIntStateOf(if (approvals.isEmpty() && deepLinkSessionRequestId == null) 1 else 0)
    }

    // Navigate to Approvals tab when a deep link arrives
    LaunchedEffect(deepLinkSessionRequestId) {
        if (deepLinkSessionRequestId != null) {
            selectedTabIndex = 0
        }
    }

    // Update tab selection when approvals change (but don't override a deep link navigation)
    LaunchedEffect(approvals) {
        if (deepLinkSessionRequestId == null) {
            selectedTabIndex = if (approvals.isEmpty()) 1 else 0
        }
    }

    val tabs = buildList {
        if (approvals.isNotEmpty() || selectedTabIndex == 0 || deepLinkSessionRequestId != null) {
            add("Approvals")
        }
        add("API Keys")
        add("KV Entries")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KV Admin") },
                actions = {
                    if (loadingApprovals) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    TextButton(onClick = onLogout) {
                        Text("Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            if (title == "Approvals" && approvals.isNotEmpty()) {
                                BadgedBox(badge = {
                                    Badge { Text(approvals.size.toString()) }
                                }) {
                                    Text(title)
                                }
                            } else {
                                Text(title)
                            }
                        },
                    )
                }
            }

            when (tabs.getOrNull(selectedTabIndex)) {
                "Approvals" -> ApprovalsScreen(
                    prefs = prefs,
                    onLogout = onLogout,
                    focusedSessionRequestId = deepLinkSessionRequestId,
                )
                "API Keys" -> KeysScreen(prefs = prefs, onLogout = onLogout)
                "KV Entries" -> KvEntriesScreen(prefs = prefs, onLogout = onLogout)
            }
        }
    }
}