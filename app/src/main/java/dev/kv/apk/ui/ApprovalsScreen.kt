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
import dev.kv.apk.data.ApproveRequest
import dev.kv.apk.data.Prefs
import dev.kv.apk.data.buildApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Must match EMOJI_POOL in kv_manager/src/keys/generate.rs
private val EMOJI_POOL: List<Pair<String, String>> = listOf(
    "🦊" to "fox", "🐬" to "dolphin", "🎸" to "guitar", "🌊" to "wave",
    "🔥" to "fire", "⚡" to "lightning", "🌈" to "rainbow", "🎯" to "target",
    "🦋" to "butterfly", "🌙" to "moon", "⭐" to "star", "🎪" to "circus",
    "🦁" to "lion", "🐉" to "dragon", "🌺" to "hibiscus", "🎨" to "palette",
    "🔮" to "crystal ball", "🎭" to "theater", "🦄" to "unicorn", "🌸" to "blossom",
    "🎵" to "music", "🏔" to "mountain", "🌿" to "herb", "🦅" to "eagle",
    "🐧" to "penguin", "🦀" to "crab", "🌴" to "palm tree", "🎃" to "pumpkin",
    "🦩" to "flamingo", "🐙" to "octopus", "🌋" to "volcano", "🎠" to "carousel",
    "🦜" to "parrot", "🐳" to "whale", "🌵" to "cactus", "🎡" to "ferris wheel",
    "🦢" to "swan", "🐝" to "bee", "🌻" to "sunflower", "🎺" to "trumpet",
    "🦚" to "peacock", "🐞" to "ladybug", "🌾" to "wheat", "🎻" to "violin",
    "🦝" to "raccoon", "🍄" to "mushroom", "🎲" to "dice", "🦠" to "microbe",
    "🌍" to "globe", "🏜" to "desert", "🎳" to "bowling", "🦌" to "deer",
    "🌠" to "shooting star", "🏝" to "island", "🦏" to "rhinoceros", "🌌" to "galaxy",
    "🏕" to "camping", "🦓" to "zebra", "🌅" to "sunrise",
)

// Deduplicated for display (server pool has some duplicates)
private val UNIQUE_EMOJI_POOL = EMOJI_POOL.distinctBy { it.first }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(prefs: Prefs, onLogout: () -> Unit) {
    val api = remember { buildApi(prefs.token) }
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
private fun ApprovalCard(
    item: ApprovalItem,
    onApprove: (String) -> Unit,
    onReject: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(listOf<String>()) }

    val filtered = remember(query) {
        if (query.isBlank()) UNIQUE_EMOJI_POOL
        else UNIQUE_EMOJI_POOL.filter { (_, name) -> name.contains(query.trim(), ignoreCase = true) }
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
