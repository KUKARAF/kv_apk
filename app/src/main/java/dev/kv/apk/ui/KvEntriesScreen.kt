package dev.kv.apk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.data.CreateKvRequest
import dev.kv.apk.data.KvApi
import dev.kv.apk.data.KvEntryItem
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.KvInk
import dev.kv.apk.ui.theme.KvOrange
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.launch

@Composable
fun KvEntriesScreen(
    api: KvApi,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<KvEntryItem>>(emptyList()) }
    var toast by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }

    var kvKey by remember { mutableStateOf("") }
    var kvScope by remember { mutableStateOf("") }
    var kvValue by remember { mutableStateOf("") }
    var kvTtl by remember { mutableStateOf("") }
    var kvSliding by remember { mutableStateOf(false) }
    var kvOpenAccess by remember { mutableStateOf(false) }
    var kvOneTime by remember { mutableStateOf(false) }
    var kvApproval by remember { mutableStateOf(false) }
    var kvZeroTrust by remember { mutableStateOf(false) }

    var genLinkDesc by remember { mutableStateOf("") }
    var genLinkScope by remember { mutableStateOf("") }

    var importText by remember { mutableStateOf("") }
    var importScope by remember { mutableStateOf("") }

    fun loadEntries() {
        scope.launch {
            try {
                entries = api.listKvEntries()
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) onLogout()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) { loadEntries() }

    val filtered = if (search.isBlank()) entries
    else entries.filter { it.key.uppercase().startsWith(search.uppercase()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KvBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                KvScreenHeader(title = "KV ENTRIES", onBack = onBack)
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                item {
                    KvCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            KvSectionTitle("ADD / UPDATE ENTRY")

                            KvLabel("KEY")
                            KvInput(
                                value = kvKey,
                                onValueChange = { kvKey = it },
                                placeholder = "DATABASE_URL",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 13.dp),
                            )

                            KvLabel("SCOPE")
                            KvInput(
                                value = kvScope,
                                onValueChange = { kvScope = it },
                                placeholder = "osmosis/deployment",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 13.dp),
                            )

                            KvLabel("VALUE")
                            KvInput(
                                value = kvValue,
                                onValueChange = { kvValue = it },
                                placeholder = "the value…",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 13.dp),
                                singleLine = false,
                                maxLines = 4,
                                fontSize = 17,
                            )

                            KvLabel("TTL (H)")
                            KvInput(
                                value = kvTtl,
                                onValueChange = { kvTtl = it },
                                placeholder = "—",
                                modifier = Modifier.padding(bottom = 15.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )

                            Column(
                                modifier = Modifier.padding(bottom = 17.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    KvCheckbox(
                                        checked = kvSliding,
                                        label = "Sliding",
                                        onToggle = { kvSliding = !kvSliding },
                                    )
                                    KvCheckbox(
                                        checked = kvOpenAccess,
                                        label = "Open access",
                                        onToggle = { kvOpenAccess = !kvOpenAccess },
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    KvCheckbox(
                                        checked = kvOneTime,
                                        label = "One-time",
                                        onToggle = { kvOneTime = !kvOneTime },
                                    )
                                    KvCheckbox(
                                        checked = kvApproval,
                                        label = "Approval",
                                        onToggle = { kvApproval = !kvApproval },
                                        color = KvOrange,
                                    )
                                }
                                KvCheckbox(
                                    checked = kvZeroTrust,
                                    label = "Zero Trust",
                                    onToggle = { kvZeroTrust = !kvZeroTrust },
                                    color = KvOrange,
                                )
                            }

                            KvButton(
                                text = "SAVE",
                                enabled = kvKey.isNotBlank() && kvValue.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        try {
                                            api.setKvEntry(
                                                CreateKvRequest(
                                                    key = kvKey.trim(),
                                                    scope = kvScope.trim(),
                                                    value = kvValue,
                                                    ttl = kvTtl.toIntOrNull(),
                                                    sliding = kvSliding,
                                                    openAccess = kvOpenAccess,
                                                    oneTime = kvOneTime,
                                                    approval = kvApproval,
                                                    zeroTrust = kvZeroTrust,
                                                )
                                            )
                                            toast = "saved ${kvKey.trim()}"
                                            kvKey = ""
                                            kvValue = ""
                                            loadEntries()
                                        } catch (e: retrofit2.HttpException) {
                                            if (e.code() == 401) onLogout()
                                            else toast = "error ${e.code()}"
                                        } catch (e: Exception) {
                                            toast = e.message ?: "error"
                                        }
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                item {
                    Row(modifier = Modifier.padding(bottom = 6.dp)) {
                        KvInput(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = "Filter by prefix",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .padding(top = 14.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("KEY", fontFamily = PressStart2P, fontSize = 8.sp, color = KvDim)
                        Text("SCOPE", fontFamily = PressStart2P, fontSize = 8.sp, color = KvDim)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(KvFaint),
                    )
                }

                items(filtered, key = { "${it.key}|${it.scope}" }) { entry ->
                    EntryRow(entry)
                }

                item {
                    Spacer(Modifier.height(18.dp))
                    KvCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            KvSectionTitle("GENERATE REQUEST LINK")
                            Text(
                                "Share a one-time link so someone can provide you with secrets. Deactivates after first submission.",
                                fontFamily = VT323,
                                fontSize = 16.sp,
                                color = KvDim,
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(bottom = 13.dp),
                            )
                            KvInput(
                                value = genLinkDesc,
                                onValueChange = { genLinkDesc = it },
                                placeholder = "Description (shown to user)",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 9.dp),
                                fontSize = 17,
                            )
                            KvInput(
                                value = genLinkScope,
                                onValueChange = { genLinkScope = it },
                                placeholder = "Scope  e.g. myapp/production",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp),
                                fontSize = 17,
                            )
                            KvButton(
                                text = "GENERATE LINK",
                                onClick = { toast = "request link generated" },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                item {
                    KvCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            KvSectionTitle("IMPORT .ENV")
                            KvInput(
                                value = importText,
                                onValueChange = { importText = it },
                                placeholder = "KEY=value\nANOTHER=value",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 11.dp),
                                singleLine = false,
                                maxLines = 5,
                                fontSize = 17,
                            )
                            KvInput(
                                value = importScope,
                                onValueChange = { importScope = it },
                                placeholder = "Scope",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp),
                                fontSize = 17,
                            )
                            KvButton(
                                text = "IMPORT",
                                enabled = importText.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        val lines = importText.lines()
                                            .map { it.trim() }
                                            .filter { it.contains('=') && !it.startsWith('#') }
                                        var imported = 0
                                        for (line in lines) {
                                            val idx = line.indexOf('=')
                                            val k = line.substring(0, idx).trim()
                                            val v = line.substring(idx + 1).trim()
                                            if (k.isNotEmpty()) {
                                                try {
                                                    api.setKvEntry(
                                                        CreateKvRequest(key = k, scope = importScope.trim(), value = v)
                                                    )
                                                    imported++
                                                } catch (_: Exception) {}
                                            }
                                        }
                                        toast = ".env imported ($imported keys)"
                                        importText = ""
                                        loadEntries()
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(80.dp))
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            KvToast(toast)
        }

        ScanlineOverlay()
    }
}

@Composable
private fun EntryRow(item: KvEntryItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.key, fontFamily = VT323, fontSize = 18.sp, color = KvInk, lineHeight = 18.sp)
            if (item.zt) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "ZERO TRUST",
                    fontFamily = PressStart2P,
                    fontSize = 6.sp,
                    color = KvOrange,
                    modifier = Modifier
                        .background(KvOrange.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                        .border(1.dp, KvOrange.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                )
            }
        }
        if (!item.scope.isNullOrEmpty()) {
            KvChip(item.scope)
        } else {
            Text("+ scope", fontFamily = VT323, fontSize = 15.sp, color = KvFaint)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KvFaint),
    )
}
