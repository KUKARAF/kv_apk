package dev.kv.apk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.data.ApprovalItem
import dev.kv.apk.data.ApproveRequest
import dev.kv.apk.data.KvApi
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.KvInk
import dev.kv.apk.ui.theme.KvOrange
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.launch

@Composable
fun ApprovalsScreen(
    api: KvApi,
    approvals: List<ApprovalItem>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onApprovalChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KvBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                KvScreenHeader(title = "APPROVALS", onBack = onBack)
            }

            Text(
                "Pending requests to release secrets. Approve to grant the requester access.",
                fontFamily = VT323,
                fontSize = 16.sp,
                color = KvDim,
                lineHeight = 19.sp,
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 16.dp),
            )

            if (approvals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "[ INBOX ZERO ]",
                            fontFamily = PressStart2P,
                            fontSize = 9.sp,
                            color = KvFaint,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("no pending requests", fontFamily = VT323, fontSize = 16.sp, color = KvDim)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                ) {
                    items(approvals, key = { it.id }) { item ->
                        ApprovalCard(
                            item = item,
                            onApprove = {
                                scope.launch {
                                    try {
                                        api.approve(item.id, ApproveRequest(""))
                                        toast = "approved"
                                        onApprovalChanged()
                                    } catch (e: retrofit2.HttpException) {
                                        if (e.code() == 401) onLogout()
                                        else toast = "error ${e.code()}"
                                    } catch (e: Exception) {
                                        toast = e.message ?: "error"
                                    }
                                }
                            },
                            onDeny = {
                                scope.launch {
                                    try {
                                        api.reject(item.id)
                                        toast = "denied"
                                        onApprovalChanged()
                                    } catch (e: retrofit2.HttpException) {
                                        if (e.code() == 401) onLogout()
                                        else toast = "error ${e.code()}"
                                    } catch (e: Exception) {
                                        toast = e.message ?: "error"
                                    }
                                }
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
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
private fun ApprovalCard(
    item: ApprovalItem,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    KvCard(
        modifier = Modifier.fillMaxWidth(),
        cornerColor = KvOrange,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulsingDot(KvOrange, 7.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    item.key ?: item.apiKeyLabel,
                    fontFamily = VT323,
                    fontSize = 19.sp,
                    color = KvInk,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 13.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    if (!item.requester.isNullOrEmpty()) {
                        Text(
                            "by ${item.requester}${if (!item.ip.isNullOrEmpty()) " @ ${item.ip}" else ""}",
                            fontFamily = VT323,
                            fontSize = 15.sp,
                            color = KvDim,
                        )
                    }
                    Text(item.requestedAt, fontFamily = VT323, fontSize = 15.sp, color = KvDim)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                KvButton(text = "APPROVE", onClick = onApprove)
                KvButtonDanger(text = "DENY", onClick = onDeny)
            }
        }
    }
}
