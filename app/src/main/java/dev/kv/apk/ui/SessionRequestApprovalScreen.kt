package dev.kv.apk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.data.ApproveSessionRequestBody
import dev.kv.apk.data.KvApi
import dev.kv.apk.data.SessionRequestDetails
import dev.kv.apk.ui.theme.KvAccent
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDanger
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.launch

private data class DurationOpt(val label: String, val hours: Long)

private val DURATIONS = listOf(
    DurationOpt("8 hours", 8),
    DurationOpt("24 hours", 24),
    DurationOpt("7 days", 168),
    DurationOpt("30 days", 720),
    DurationOpt("90 days", 2160),
    DurationOpt("180 days", 4320),
    DurationOpt("365 days", 8760),
)

private fun closestDuration(hours: Long?): DurationOpt {
    if (hours == null) return DURATIONS[3] // 30d default
    return DURATIONS.minByOrNull { kotlin.math.abs(it.hours - hours) } ?: DURATIONS[3]
}

@Composable
fun SessionRequestApprovalScreen(
    api: KvApi,
    requestId: String,
    onDone: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf<SessionRequestDetails?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedDuration by remember { mutableStateOf(DURATIONS[3]) }
    var durationMenuExpanded by remember { mutableStateOf(false) }
    var actionLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(requestId) {
        try {
            val d = api.getSessionRequest(requestId)
            details = d
            selectedDuration = closestDuration(d.requestedDurationHours)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) onLogout()
            else error = "HTTP ${e.code()}"
        } catch (e: Exception) {
            error = e.message ?: "Failed to load request"
        } finally {
            loading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KvBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            KvScreenHeader(title = "APPROVE SESSION", onBack = onDone)

            Spacer(Modifier.height(20.dp))

            when {
                loading -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = KvAccent)
                    }
                }

                result != null -> {
                    KvCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(result!!, fontFamily = VT323, fontSize = 20.sp, color = KvAccent)
                            Spacer(Modifier.height(16.dp))
                            KvButton(text = "BACK", onClick = onDone, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                error != null -> {
                    Text(error!!, fontFamily = VT323, fontSize = 16.sp, color = KvDanger)
                    Spacer(Modifier.height(12.dp))
                    KvButton(text = "BACK", onClick = onDone)
                }

                details != null -> {
                    val d = details!!

                    if (d.status != "pending") {
                        KvCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp)) {
                                KvSectionTitle("REQUEST STATUS")
                                Text(
                                    "This request is already ${d.status}.",
                                    fontFamily = VT323,
                                    fontSize = 18.sp,
                                    color = KvDim,
                                )
                                Spacer(Modifier.height(16.dp))
                                KvButton(text = "BACK", onClick = onDone, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        return@Column
                    }

                    KvCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            KvSectionTitle("REQUEST DETAILS")

                            KvLabel("FROM")
                            Text(
                                d.label ?: "(no label)",
                                fontFamily = VT323,
                                fontSize = 20.sp,
                                color = KvAccent,
                                modifier = Modifier.padding(bottom = 14.dp),
                            )

                            KvLabel("REQUESTED AT")
                            Text(
                                d.requestedAt,
                                fontFamily = VT323,
                                fontSize = 16.sp,
                                color = KvDim,
                                modifier = Modifier.padding(bottom = 14.dp),
                            )

                            if (d.requestedDurationHours != null) {
                                KvLabel("CLIENT REQUESTED")
                                Text(
                                    closestDuration(d.requestedDurationHours).label,
                                    fontFamily = VT323,
                                    fontSize = 16.sp,
                                    color = KvDim,
                                    modifier = Modifier.padding(bottom = 14.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    KvCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            KvSectionTitle("APPROVE SESSION")
                            KvLabel("SESSION DURATION")
                            Box {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF070D07), RoundedCornerShape(2.dp))
                                        .border(1.dp, Color(0xFF2a2a2a), RoundedCornerShape(2.dp))
                                        .clickable { durationMenuExpanded = true }
                                        .padding(10.dp),
                                ) {
                                    Text(
                                        selectedDuration.label + "  ▾",
                                        fontFamily = VT323,
                                        fontSize = 15.sp,
                                        color = KvAccent,
                                    )
                                }
                                DropdownMenu(
                                    expanded = durationMenuExpanded,
                                    onDismissRequest = { durationMenuExpanded = false },
                                ) {
                                    DURATIONS.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt.label, fontFamily = VT323, fontSize = 15.sp) },
                                            onClick = { selectedDuration = opt; durationMenuExpanded = false },
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                KvButtonDanger(
                                    text = "REJECT",
                                    enabled = !actionLoading,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        scope.launch {
                                            actionLoading = true
                                            try {
                                                val resp = api.rejectSessionRequest(requestId)
                                                result = if (resp.isSuccessful) "Request rejected." else "Error: HTTP ${resp.code()}"
                                            } catch (e: Exception) {
                                                error = e.message
                                            } finally {
                                                actionLoading = false
                                            }
                                        }
                                    },
                                )
                                KvButton(
                                    text = "APPROVE",
                                    enabled = !actionLoading,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        scope.launch {
                                            actionLoading = true
                                            try {
                                                val resp = api.approveSessionRequest(
                                                    requestId,
                                                    ApproveSessionRequestBody(selectedDuration.hours),
                                                )
                                                result = if (resp.isSuccessful)
                                                    "Approved! Token valid for ${selectedDuration.label}."
                                                else
                                                    "Error: HTTP ${resp.code()}"
                                            } catch (e: Exception) {
                                                error = e.message
                                            } finally {
                                                actionLoading = false
                                            }
                                        }
                                    },
                                )
                            }

                            if (actionLoading) {
                                Spacer(Modifier.height(10.dp))
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(20.dp),
                                        color = KvAccent,
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ScanlineOverlay()
    }
}
