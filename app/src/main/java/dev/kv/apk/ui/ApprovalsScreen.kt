package dev.kv.apk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.data.ApprovalItem
import dev.kv.apk.data.Prefs
import dev.kv.apk.data.buildApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(prefs: Prefs, onLogout: () -> Unit) {
    val api = remember { buildApi(prefs.serverUrl, prefs.token) }
    val scope = rememberCoroutineScope()

    var approvals by remember { mutableStateOf<List<ApprovalItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        loading = true
        error = null
        try {
            approvals = api.listApprovals()
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) onLogout() else error = "HTTP ${e.code()}"
        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
        } finally {
            loading = false
        }
    }

    // Auto-refresh every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            refreshTick++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pending Approvals") },
                actions = {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(onClick = { refreshTick++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                error != null -> Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                approvals.isEmpty() && !loading -> Text(
                    text = "No pending approvals",
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(approvals, key = { it.id }) { item ->
                        ApprovalCard(
                            item = item,
                            onApprove = {
                                scope.launch {
                                    runCatching { api.approve(item.id) }
                                        .onSuccess { resp ->
                                            if (resp.code() == 401) onLogout()
                                            else approvals = approvals.filter { it.id != item.id }
                                        }
                                }
                            },
                            onReject = {
                                scope.launch {
                                    runCatching { api.reject(item.id) }
                                        .onSuccess { resp ->
                                            if (resp.code() == 401) onLogout()
                                            else approvals = approvals.filter { it.id != item.id }
                                        }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(item: ApprovalItem, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(item.emojiSequence, fontSize = 40.sp)
            Spacer(Modifier.height(8.dp))
            Text(item.apiKeyLabel, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Requested: ${item.requestedAt}", style = MaterialTheme.typography.bodySmall)
            Text("Expires:   ${item.expiresAt}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                    modifier = Modifier.weight(1f),
                ) { Text("Approve") }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                ) { Text("Reject") }
            }
        }
    }
}
