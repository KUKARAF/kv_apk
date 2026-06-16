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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.data.AccessLogEntry
import dev.kv.apk.data.BlockedIpItem
import dev.kv.apk.data.KvApi
import dev.kv.apk.data.RateLimitRow
import dev.kv.apk.ui.theme.KvAccent
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDanger
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.KvInk
import dev.kv.apk.ui.theme.KvOrange
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.launch

@Composable
fun RateLimitsScreen(
    api: KvApi,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var blocked by remember { mutableStateOf<List<BlockedIpItem>>(emptyList()) }
    var rateRows by remember { mutableStateOf<List<RateLimitRow>>(emptyList()) }
    var accessLog by remember { mutableStateOf<List<AccessLogEntry>>(emptyList()) }
    var toast by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            try {
                blocked = api.listBlockedIps()
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) onLogout()
            } catch (_: Exception) {}
            try {
                val limits = api.getRateLimits()
                rateRows = limits.rows
                accessLog = limits.accessLog
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) { load() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KvBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                KvScreenHeader(title = "RATE LIMITS", onBack = onBack)
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                item {
                    Text(
                        "BLOCKED IPS",
                        fontFamily = PressStart2P,
                        fontSize = 11.sp,
                        color = KvInk,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(
                        "Permanently blocked IPs from repeated auth failures. Unblocking requires an active admin session.",
                        fontFamily = VT323,
                        fontSize = 15.sp,
                        color = KvDim,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                items(blocked, key = { it.id }) { item ->
                    BlockedIpCard(
                        item = item,
                        onUnblock = {
                            scope.launch {
                                try {
                                    api.unblockIp(item.ip)
                                    toast = "unblocked ${item.ip}"
                                    load()
                                } catch (e: retrofit2.HttpException) {
                                    if (e.code() == 401) onLogout()
                                    else toast = "error ${e.code()}"
                                } catch (e: Exception) {
                                    toast = e.message ?: "error"
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(9.dp))
                }

                item {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "RATE LIMITS",
                        fontFamily = PressStart2P,
                        fontSize = 11.sp,
                        color = KvInk,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(
                        "Request counts per IP since last daily reset. Authenticated requests are exempt.",
                        fontFamily = VT323,
                        fontSize = 15.sp,
                        color = KvDim,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("IP", fontFamily = PressStart2P, fontSize = 7.sp, color = KvDim)
                        Text("REQ TODAY", fontFamily = PressStart2P, fontSize = 7.sp, color = KvDim)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(KvFaint))
                }

                items(rateRows) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(row.ip, fontFamily = VT323, fontSize = 18.sp, color = KvInk)
                        Text(row.count.toString(), fontFamily = VT323, fontSize = 18.sp, color = KvAccent)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(KvFaint))
                }

                item {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "ACCESS LOG",
                        fontFamily = PressStart2P,
                        fontSize = 11.sp,
                        color = KvInk,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(
                        "Last authenticated KV operations (newest first).",
                        fontFamily = VT323,
                        fontSize = 15.sp,
                        color = KvDim,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                items(accessLog) { entry ->
                    AccessLogCard(entry)
                    Spacer(Modifier.height(8.dp))
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            KvToast(toast)
        }

        ScanlineOverlay()
    }
}

@Composable
private fun BlockedIpCard(item: BlockedIpItem, onUnblock: () -> Unit) {
    KvCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp, 12.dp, 13.dp, 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.ip, fontFamily = VT323, fontSize = 19.sp, color = KvInk)
                KvButtonOutline(text = "UNBLOCK", onClick = onUnblock)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "failures ${item.failures}",
                    fontFamily = VT323,
                    fontSize = 15.sp,
                    color = KvDim,
                )
                Text(
                    "status ${item.blockedAt}",
                    fontFamily = VT323,
                    fontSize = 15.sp,
                    color = KvOrange,
                )
                Text(
                    "last ${item.lastSeen}",
                    fontFamily = VT323,
                    fontSize = 15.sp,
                    color = KvDim,
                )
            }
        }
    }
}

@Composable
private fun AccessLogCard(entry: AccessLogEntry) {
    KvCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(11.dp, 11.dp, 12.dp, 11.dp)) {
            Row(
                modifier = Modifier.padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.op.uppercase(),
                    fontFamily = PressStart2P,
                    fontSize = 7.sp,
                    color = KvAccent,
                    modifier = Modifier
                        .border(1.dp, KvAccent.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                )
                Text(
                    "  ${entry.time}",
                    fontFamily = VT323,
                    fontSize = 16.sp,
                    color = KvDim,
                )
            }
            Text(entry.key, fontFamily = VT323, fontSize = 17.sp, color = KvInk, lineHeight = 17.sp)
            Text(
                "${entry.ip} · keyid ${entry.keyId}",
                fontFamily = VT323,
                fontSize = 14.sp,
                color = KvDim,
            )
        }
    }
}
