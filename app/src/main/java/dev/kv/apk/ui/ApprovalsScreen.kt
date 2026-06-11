package dev.kv.apk.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.data.ApprovalItem
import dev.kv.apk.data.ApproveDeviceRequest
import dev.kv.apk.data.ApproveRequest
import dev.kv.apk.data.DeviceAuthItem
import dev.kv.apk.data.EmojiEntry
import dev.kv.apk.data.Prefs
import dev.kv.apk.data.SessionRequestItem
import dev.kv.apk.data.buildApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(
    prefs: Prefs,
    onLogout: () -> Unit,
    focusedSessionRequestId: String? = null,
) {
    val api = remember { buildApi(prefs.token) }
    val scope = rememberCoroutineScope()

    var approvals by remember { mutableStateOf<List<ApprovalItem>>(emptyList()) }
    var deviceAuths by remember { mutableStateOf<List<DeviceAuthItem>>(emptyList()) }
    var sessionRequests by remember { mutableStateOf<List<SessionRequestItem>>(emptyList()) }
    var emojiPool by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }

    // Load emoji pool once from the server (same source as the backend)
    LaunchedEffect(Unit) {
        runCatching { api.getEmojis() }
            .onSuccess { entries -> emojiPool = entries.map { it.e to it.n } }
    }

    LaunchedEffect(refreshTick) {
        loading = true
        error = null
        try {
            approvals = api.listApprovals()
            deviceAuths = api.listDeviceAuthRequests()
            sessionRequests = api.listSessionRequests()
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
                approvals.isEmpty() && deviceAuths.isEmpty() && sessionRequests.isEmpty() && !loading -> Text(
                    text = "No pending approvals",
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (sessionRequests.isNotEmpty()) {
                        item(key = "session-request-header") {
                            Text(
                                text = "Session Requests",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                        items(sessionRequests, key = { "session-req-${it.id}" }) { item ->
                            SessionRequestCard(
                                item = item,
                                highlighted = item.id == focusedSessionRequestId,
                                onApprove = {
                                    scope.launch {
                                        runCatching { api.approveSessionRequest(item.id) }
                                            .onSuccess { resp ->
                                                if (resp.code() == 401) onLogout()
                                                else refreshTick++
                                            }
                                            .onFailure { }
                                    }
                                },
                                onReject = {
                                    scope.launch {
                                        runCatching { api.rejectSessionRequest(item.id) }
                                            .onSuccess { resp ->
                                                if (resp.code() == 401) onLogout()
                                                else refreshTick++
                                            }
                                            .onFailure { }
                                    }
                                },
                            )
                        }
                    }
                    if (deviceAuths.isNotEmpty()) {
                        item(key = "device-auth-header") {
                            Text(
                                text = "Device Auth Requests",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                        items(deviceAuths, key = { "device-${it.id}" }) { item ->
                            DeviceAuthCard(
                                item = item,
                                onApprove = { label, scopes ->
                                    scope.launch {
                                        runCatching {
                                            api.approveDevice(
                                                item.id,
                                                ApproveDeviceRequest(
                                                    label = label,
                                                    scopes = scopes,
                                                    expiresAt = item.expiresAt,
                                                )
                                            )
                                        }
                                            .onSuccess { resp ->
                                                if (resp.code() == 401) onLogout()
                                                else refreshTick++
                                            }
                                            .onFailure { /* network error — leave item, user can retry */ }
                                    }
                                },
                                onReject = {
                                    scope.launch {
                                        runCatching { api.rejectDevice(item.id) }
                                            .onSuccess { resp ->
                                                if (resp.code() == 401) onLogout()
                                                else refreshTick++
                                            }
                                            .onFailure { /* network error */ }
                                    }
                                },
                            )
                        }
                        item(key = "approvals-header") {
                            Text(
                                text = "API Key Approvals",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                    items(approvals, key = { it.id }) { item ->
                        ApprovalCard(
                            item = item,
                            emojiPool = emojiPool,
                            onApprove = { confirmString ->
                                scope.launch {
                                    runCatching { api.approve(item.id, ApproveRequest(confirmString)) }
                                        .onSuccess { resp ->
                                            if (resp.code() == 401) onLogout()
                                            else refreshTick++
                                        }
                                        .onFailure { /* network error — leave item, user can retry */ }
                                }
                            },
                            onReject = {
                                scope.launch {
                                    runCatching { api.reject(item.id) }
                                        .onSuccess { resp ->
                                            if (resp.code() == 401) onLogout()
                                            else refreshTick++
                                        }
                                        .onFailure { /* network error */ }
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
private fun SessionRequestCard(
    item: SessionRequestItem,
    highlighted: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = if (highlighted)
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else
            CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = item.label ?: "Unnamed client",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text("Requested: ${item.requestedAt}", style = MaterialTheme.typography.bodySmall)
            Text("Expires:   ${item.expiresAt}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Approving creates a 15-hour session token.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
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

@Composable
private fun ApprovalCard(
    item: ApprovalItem,
    emojiPool: List<Pair<String, String>>,
    onApprove: (String) -> Unit,
    onReject: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(listOf<String>()) }

    val filtered = remember(query, emojiPool) {
        if (query.isBlank()) emojiPool
        else emojiPool.filter { (_, name) -> name.contains(query.trim(), ignoreCase = true) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(item.apiKeyLabel, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Requested: ${item.requestedAt}", style = MaterialTheme.typography.bodySmall)
            Text("Expires:   ${item.expiresAt}", style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(12.dp))
            Text("Pin the 3 emojis shown to the requester:", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))

            // 3 pinned slots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    val emoji = pinned.getOrNull(i)
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .border(
                                width = 1.dp,
                                color = if (emoji != null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .then(
                                if (emoji != null) Modifier.clickable {
                                    pinned = pinned.toMutableList().also { it.removeAt(i) }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (emoji != null) {
                            Text(emoji, fontSize = 28.sp)
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(14.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        } else {
                            Text("?", fontSize = 22.sp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search emoji by name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))

            if (filtered.isEmpty()) {
                Text(
                    "No match",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.first }) { (emoji, name) ->
                        val alreadyFull = pinned.size >= 3
                        TextButton(
                            onClick = {
                                if (!alreadyFull) {
                                    pinned = pinned + emoji
                                    query = ""
                                }
                            },
                            enabled = !alreadyFull,
                            contentPadding = PaddingValues(4.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(emoji, fontSize = 24.sp)
                                Text(name, fontSize = 9.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onApprove(pinned.joinToString("")) },
                    enabled = pinned.size == 3,
                    modifier = Modifier.weight(1f),
                ) { Text("Confirm") }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                ) { Text("Reject") }
            }
        }
    }
}

@Composable
private fun DeviceAuthCard(
    item: DeviceAuthItem,
    onApprove: (String, List<String>) -> Unit,
    onReject: () -> Unit,
) {
    var showApproveDialog by remember { mutableStateOf(false) }
    var labelInput by remember { mutableStateOf(item.label ?: "") }
    var scopesInput by remember { mutableStateOf("default") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = item.label ?: "Unnamed device",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text("Requested: ${item.requestedAt}", style = MaterialTheme.typography.bodySmall)
            if (item.expiresAt != null) {
                Text("Expires:   ${item.expiresAt}", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showApproveDialog = true },
                    modifier = Modifier.weight(1f),
                ) { Text("Approve") }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                ) { Text("Reject") }
            }
        }
    }

    if (showApproveDialog) {
        AlertDialog(
            onDismissRequest = { showApproveDialog = false },
            title = { Text("Approve Device") },
            text = {
                Column {
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text("Device label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = scopesInput,
                        onValueChange = { scopesInput = it },
                        label = { Text("Scopes (comma-separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val scopes = scopesInput.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        onApprove(labelInput.ifBlank { item.label ?: "" }, scopes)
                        showApproveDialog = false
                    },
                ) { Text("Approve") }
            },
            dismissButton = {
                TextButton(onClick = { showApproveDialog = false }) { Text("Cancel") }
            },
        )
    }
}
