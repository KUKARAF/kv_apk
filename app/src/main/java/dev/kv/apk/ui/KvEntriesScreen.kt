package dev.kv.apk.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kv.apk.data.KvEntryItem
import dev.kv.apk.data.Prefs
import dev.kv.apk.data.buildApi
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KvEntriesScreen(prefs: Prefs, onLogout: () -> Unit) {
    val api = remember { buildApi(prefs.token) }
    val context = LocalContext.current

    var entries by remember { mutableStateOf<List<KvEntryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        loading = true
        error = null
        try {
            entries = api.listKvEntries()
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
                title = { Text("KV Entries") },
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
                entries.isEmpty() && !loading -> Text(
                    text = "No KV entries found",
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.key }) { item ->
                        KvEntryCard(item, context)
                    }
                }
            }
        }
    }
}

@Composable
private fun KvEntryCard(item: KvEntryItem, context: Context) {
    var masked by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.key,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {},
                    label = { Text(item.scope) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (masked) "••••••••" else item.value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = if (!masked) FontFamily.Monospace else FontFamily.Default
                    ),
                    maxLines = if (masked) 1 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                IconButton(onClick = { masked = !masked }) {
                    Icon(
                        if (masked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (masked) "Show value" else "Hide value",
                    )
                }

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("kv_value", item.value)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy value")
                }
            }
        }
    }
}