package dev.kv.apk.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.data.DeviceAuthRequest
import dev.kv.apk.data.buildDeviceAuthApi
import dev.kv.apk.ui.theme.KvAccent
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDanger
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.KvInk
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(onRegistered: (token: String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var deviceName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var approvalUrl by remember { mutableStateOf<String?>(null) }
    var requestId by remember { mutableStateOf<String?>(null) }
    var polling by remember { mutableStateOf(false) }

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
                .padding(horizontal = 20.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("KV·VAULT", fontFamily = PressStart2P, fontSize = 22.sp, color = KvAccent)
            Spacer(Modifier.height(6.dp))
            Text("secrets manager // mobile", fontFamily = VT323, fontSize = 16.sp, color = KvDim)

            Spacer(Modifier.height(40.dp))

            Text(
                "> REGISTER DEVICE",
                fontFamily = PressStart2P,
                fontSize = 9.sp,
                color = KvDim,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            if (approvalUrl == null) {
                KvCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp)) {
                        KvSectionTitle("DEVICE NAME")
                        KvLabel("NAME")
                        KvInput(
                            value = deviceName,
                            onValueChange = { deviceName = it },
                            placeholder = "pixel pro",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 15.dp),
                        )

                        if (error != null) {
                            Text(
                                error!!,
                                fontFamily = VT323,
                                fontSize = 15.sp,
                                color = KvDanger,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            KvButton(
                                text = "START",
                                enabled = deviceName.isNotBlank() && !loading,
                                onClick = {
                                    scope.launch {
                                        loading = true
                                        error = null
                                        try {
                                            val api = buildDeviceAuthApi()
                                            val resp = api.createRequest(
                                                DeviceAuthRequest(label = deviceName.trim())
                                            )
                                            requestId = resp.id
                                            approvalUrl = resp.url
                                        } catch (e: Exception) {
                                            error = e.message ?: "Failed to create request"
                                        } finally {
                                            loading = false
                                        }
                                    }
                                },
                            )
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp),
                                    color = KvAccent,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            } else {
                KvCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp)) {
                        KvSectionTitle("AWAITING APPROVAL")
                        Text(
                            "Open this link in your browser and approve the request from the admin dashboard.",
                            fontFamily = VT323,
                            fontSize = 16.sp,
                            color = KvDim,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF070D07), RoundedCornerShape(2.dp))
                                .border(1.dp, KvFaint, RoundedCornerShape(2.dp))
                                .padding(10.dp),
                        ) {
                            Text(
                                approvalUrl!!,
                                fontFamily = VT323,
                                fontSize = 13.sp,
                                color = KvAccent,
                                lineHeight = 17.sp,
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        if (error != null) {
                            Text(
                                error!!,
                                fontFamily = VT323,
                                fontSize = 15.sp,
                                color = KvDanger,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KvButton(
                                text = "OPEN",
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(approvalUrl))
                                    context.startActivity(intent)
                                },
                            )

                            KvButtonOutline(
                                text = if (polling) "POLLING…" else "CHECK",
                                enabled = !polling,
                                onClick = {
                                    scope.launch {
                                        polling = true
                                        error = null
                                        try {
                                            val api = buildDeviceAuthApi()
                                            val status = api.pollStatus(requestId!!)
                                            when (status.status) {
                                                "approved" -> {
                                                    val key = status.apiKey
                                                    if (key != null) {
                                                        onRegistered(key)
                                                    } else {
                                                        error = "approved but no key returned"
                                                    }
                                                }
                                                "rejected" -> error = "request was rejected"
                                                "delivered" -> error = "key already delivered — start over"
                                                else -> error = "status: ${status.status} — not approved yet"
                                            }
                                        } catch (e: Exception) {
                                            error = e.message ?: "poll failed"
                                        } finally {
                                            polling = false
                                        }
                                    }
                                },
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        KvButtonOutline(
                            text = "START OVER",
                            color = KvDim,
                            onClick = {
                                approvalUrl = null
                                requestId = null
                                error = null
                            },
                        )
                    }
                }

                // Auto-poll every 5 seconds while on this screen
                if (!polling) {
                    androidx.compose.runtime.LaunchedEffect(requestId) {
                        while (true) {
                            delay(5_000)
                            try {
                                val api = buildDeviceAuthApi()
                                val status = api.pollStatus(requestId!!)
                                if (status.status == "approved" && status.apiKey != null) {
                                    onRegistered(status.apiKey)
                                    break
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
            Text("kv.osmosis.page", fontFamily = VT323, fontSize = 14.sp, color = KvFaint)
        }

        ScanlineOverlay()
    }
}
