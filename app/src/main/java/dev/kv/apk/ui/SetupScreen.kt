package dev.kv.apk.ui

import android.util.Base64
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.data.DeviceRegistrationRequest
import dev.kv.apk.data.Prefs
import dev.kv.apk.data.buildApi
import dev.kv.apk.ui.theme.KvAccent
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDanger
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.launch
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

private fun generateOrLoadKeyPair(prefs: Prefs): String {
    if (prefs.hasDeviceKey()) return prefs.devicePubKeySpki

    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))
    val keyPair = kpg.generateKeyPair()

    prefs.devicePrivKeyPkcs8 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
    prefs.devicePubKeySpki = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

    return prefs.devicePubKeySpki
}

@Composable
fun SetupScreen(prefs: Prefs, onRegistered: (token: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var deviceName by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }

    val publicKey = remember { runCatching { generateOrLoadKeyPair(prefs) }.getOrElse { "" } }

    if (showScanner) {
        QrScannerScreen(
            onScanned = { scanned -> token = scanned; showScanner = false },
            onCancel = { showScanner = false },
        )
        return
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

            KvCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp)) {
                    KvSectionTitle("DEVICE IDENTITY")
                    Text(
                        "A key pair has been generated on this device. The private key never leaves your phone.",
                        fontFamily = VT323,
                        fontSize = 15.sp,
                        color = KvDim,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                    KvLabel("PUBLIC KEY")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF070D07), RoundedCornerShape(2.dp))
                            .padding(10.dp),
                    ) {
                        Text(
                            if (publicKey.length > 64) publicKey.take(32) + "…" + publicKey.takeLast(16)
                            else publicKey,
                            fontFamily = VT323,
                            fontSize = 13.sp,
                            color = KvAccent,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

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

                    KvLabel("AUTHORISATION")
                    KvButton(
                        text = if (token.isNotBlank()) "TOKEN SCANNED ✓" else "SCAN QR CODE",
                        enabled = !loading,
                        onClick = { showScanner = true },
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
                            text = "REGISTER",
                            enabled = deviceName.isNotBlank() && token.isNotBlank() && !loading && publicKey.isNotEmpty(),
                            onClick = {
                                scope.launch {
                                    loading = true
                                    error = null
                                    try {
                                        val resp = buildApi(token.trim()).registerDevice(
                                            DeviceRegistrationRequest(
                                                name = deviceName.trim(),
                                                publicKey = publicKey,
                                                keyType = "p256",
                                            )
                                        )
                                        if (resp.isSuccessful) {
                                            prefs.deviceId = resp.body()!!.id
                                            onRegistered(token.trim())
                                        } else {
                                            error = "HTTP ${resp.code()}"
                                        }
                                    } catch (e: Exception) {
                                        error = e.message ?: "Registration failed"
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

            Spacer(Modifier.height(40.dp))
            Text("kv.osmosis.page", fontFamily = VT323, fontSize = 14.sp, color = KvFaint)
        }

        ScanlineOverlay()
    }
}
