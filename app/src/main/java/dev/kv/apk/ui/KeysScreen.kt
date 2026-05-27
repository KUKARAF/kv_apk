package dev.kv.apk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kv.apk.data.ApiKeyItem
import dev.kv.apk.data.Prefs
import dev.kv.apk.data.ScopeItem
import dev.kv.apk.data.buildApi
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(prefs: Prefs, onLogout: () -> Unit) {
    val api = remember { buildApi(prefs.token) }
    val scope = rememberCoroutineScope()

    var keys by remember { mutableStateOf<List<ApiKeyItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        loading = true
        error = null
        try {
            keys = api.listKeys()
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) onLogout() else error = "HTTP ${e.code()}"
        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
        } finally {
            loading = false
        }
    }

    // Auto-refresh every 60 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            refreshTick++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API Keys") },
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
                keys.isEmpty() && !loading -> Text(
                    text = "No API keys found",
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(keys, key = { it.id }) { item ->
                        KeyCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCard(item: ApiKeyItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(item.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(item.keyType) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
                AssistChip(
                    onClick = {},
                    label = { Text(item.status) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = when (item.status.lowercase()) {
                            "active" -> MaterialTheme.colorScheme.primaryContainer
                            "revoked" -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Scopes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            if (item.scopes.isEmpty()) {
                Text(
                    "No scopes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                item.scopes.forEach { s ->
                    ScopeRow(s)
                }
            }
        }
    }
}

@Composable
private fun ScopeRow(s: ScopeItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            s.scope,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (s.ops.isNotEmpty()) {
            Text(
                s.ops,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}