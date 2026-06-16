package dev.kv.apk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import dev.kv.apk.data.KvApi
import dev.kv.apk.data.SessionInfo
import dev.kv.apk.ui.theme.KvAccent
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.KvInk
import dev.kv.apk.ui.theme.KvOrange
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.launch

@Composable
fun SessionScreen(
    api: KvApi,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<SessionInfo?>(null) }
    var deviceToken by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            session = api.getSession()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KvBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            KvScreenHeader(title = "SESSION", onBack = onBack)
            Spacer(Modifier.height(12.dp))

            KvCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    KvSectionTitle("CURRENT SESSION", color = KvOrange)

                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                        SessionField("EMAIL", session?.email ?: "—")
                        Column {
                            Text(
                                "SUBJECT",
                                fontFamily = PressStart2P,
                                fontSize = 7.sp,
                                color = KvDim,
                                modifier = Modifier.padding(bottom = 5.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF070D07), RoundedCornerShape(2.dp))
                                    .border(1.dp, KvFaint, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 9.dp, vertical = 7.dp),
                            ) {
                                Text(
                                    session?.subject ?: "—",
                                    fontFamily = VT323,
                                    fontSize = 16.sp,
                                    color = Color(0xFFBDF0C8),
                                    lineHeight = 20.sp,
                                )
                            }
                        }
                        Row {
                            SessionField("EXPIRES", session?.expiresAt ?: "—", modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(20.dp))
                            SessionField("CREATED", session?.createdAt ?: "—", modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            KvButtonOutline(
                text = "GENERATE DEVICE TOKEN (180 DAYS)",
                onClick = {
                    scope.launch {
                        try {
                            val resp = api.createSessionKey()
                            if (resp.isSuccessful) {
                                deviceToken = resp.body()?.key
                                toast = "device token generated"
                            } else if (resp.code() == 401) {
                                onLogout()
                            } else {
                                toast = "error ${resp.code()}"
                            }
                        } catch (e: Exception) {
                            toast = e.message ?: "error"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (deviceToken != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070D07), RoundedCornerShape(3.dp))
                        .border(1.dp, KvAccent.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                        .padding(13.dp),
                ) {
                    Column {
                        Text(
                            "DEVICE TOKEN · COPY NOW",
                            fontFamily = PressStart2P,
                            fontSize = 7.sp,
                            color = KvDim,
                            modifier = Modifier.padding(bottom = 7.dp),
                        )
                        Text(
                            deviceToken!!,
                            fontFamily = VT323,
                            fontSize = 18.sp,
                            color = KvAccent,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            KvButtonDanger(
                text = "LOGOUT",
                onClick = {
                    scope.launch {
                        try { api.revokeSessionKey() } catch (_: Exception) {}
                        onLogout()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(80.dp))
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            KvToast(toast)
        }

        ScanlineOverlay()
    }
}

@Composable
private fun SessionField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontFamily = PressStart2P, fontSize = 7.sp, color = KvDim, modifier = Modifier.padding(bottom = 5.dp))
        Text(value, fontFamily = VT323, fontSize = 17.sp, color = KvInk)
    }
}
