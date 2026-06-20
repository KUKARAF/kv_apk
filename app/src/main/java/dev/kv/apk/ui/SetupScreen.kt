package dev.kv.apk.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dev.kv.apk.data.CreateSessionRequestBody
import dev.kv.apk.data.DeviceRegistrationRequest
import dev.kv.apk.data.Prefs
import dev.kv.apk.data.buildApi
import dev.kv.apk.data.buildUnauthApi
import dev.kv.apk.ui.theme.KvAccent
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDanger
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

private enum class SetupPhase { FORM, WAITING, REGISTERING }

private data class DurationOption(val label: String, val hours: Long)

private val DURATION_OPTIONS = listOf(
    DurationOption("7 days", 168),
    DurationOption("30 days", 720),
    DurationOption("90 days", 2160),
    DurationOption("180 days", 4320),
    DurationOption("365 days", 8760),
)

private fun generateOrLoadKeyPair(prefs: Prefs): String {
    if (prefs.hasDeviceKey()) return prefs.devicePubKeySpki

    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))
    val keyPair = kpg.generateKeyPair()

    prefs.devicePrivKeyPkcs8 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
    prefs.devicePubKeySpki = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

    return prefs.devicePubKeySpki
}

private fun urlToQrBitmap(url: String, size: Int = 600): Bitmap {
    val bits = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size)
    return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).also { bmp ->
        for (x in 0 until size) for (y in 0 until size)
            bmp.setPixel(x, y, if (bits[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
    }
}

@Composable
fun SetupScreen(prefs: Prefs, onSetupComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val publicKey = remember { runCatching { generateOrLoadKeyPair(prefs) }.getOrElse { "" } }

    var phase by remember { mutableStateOf(SetupPhase.FORM) }
    var deviceName by remember { mutableStateOf("") }
    var selectedDuration by remember { mutableStateOf(DURATION_OPTIONS[1]) } // 30 days default
    var durationMenuExpanded by remember { mutableStateOf(false) }
    var approvalUrl by remember { mutableStateOf("") }
    var requestId by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Polling loop while in WAITING phase
    LaunchedEffect(requestId) {
        if (requestId.isBlank()) return@LaunchedEffect
        val unauthApi = buildUnauthApi()
        while (true) {
            delay(5_000)
            try {
                val status = unauthApi.pollStatus(requestId)
                when (status.status) {
                    "approved" -> {
                        val token = status.sessionToken ?: run {
                            error = "Server approved but returned no token"
                            phase = SetupPhase.FORM
                            return@LaunchedEffect
                        }
                        prefs.token = token
                        phase = SetupPhase.REGISTERING
                        return@LaunchedEffect
                    }
                    "rejected" -> {
                        error = "Request was rejected"
                        phase = SetupPhase.FORM
                        return@LaunchedEffect
                    }
                    "expired" -> {
                        error = "Request expired without approval"
                        phase = SetupPhase.FORM
                        return@LaunchedEffect
                    }
                }
            } catch (_: Exception) { /* network hiccup, keep polling */ }
        }
    }

    // Device registration once token is ready
    LaunchedEffect(phase) {
        if (phase != SetupPhase.REGISTERING) return@LaunchedEffect
        if (prefs.deviceId.isNotBlank()) {
            // Already registered — just update session
            onSetupComplete()
            return@LaunchedEffect
        }
        try {
            val resp = buildApi(prefs.token).registerDevice(
                DeviceRegistrationRequest(
                    name = deviceName.trim(),
                    publicKey = publicKey,
                    keyType = "p256",
                )
            )
            if (resp.isSuccessful) {
                prefs.deviceId = resp.body()!!.id
                onSetupComplete()
            } else {
                error = "Device registration failed: HTTP ${resp.code()}"
                phase = SetupPhase.FORM
            }
        } catch (e: Exception) {
            error = "Device registration failed: ${e.message}"
            phase = SetupPhase.FORM
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
                .padding(horizontal = 20.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("KV·VAULT", fontFamily = PressStart2P, fontSize = 22.sp, color = KvAccent)
            Spacer(Modifier.height(6.dp))
            Text("secrets manager // mobile", fontFamily = VT323, fontSize = 16.sp, color = KvDim)

            Spacer(Modifier.height(40.dp))

            when (phase) {
                SetupPhase.FORM -> {
                    Text(
                        "> REQUEST SESSION",
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
                            KvSectionTitle("SESSION REQUEST")

                            if (prefs.deviceId.isBlank()) {
                                KvLabel("DEVICE NAME")
                                KvInput(
                                    value = deviceName,
                                    onValueChange = { deviceName = it },
                                    placeholder = "pixel pro",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 15.dp),
                                )
                            } else {
                                Text(
                                    "Device already registered. Requesting new session token.",
                                    fontFamily = VT323,
                                    fontSize = 14.sp,
                                    color = KvDim,
                                    modifier = Modifier.padding(bottom = 14.dp),
                                )
                            }

                            KvLabel("REQUESTED DURATION")
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
                                    DURATION_OPTIONS.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt.label, fontFamily = VT323, fontSize = 15.sp) },
                                            onClick = {
                                                selectedDuration = opt
                                                durationMenuExpanded = false
                                            }
                                        )
                                    }
                                }
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

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                val canRequest = (prefs.deviceId.isNotBlank() || deviceName.isNotBlank()) &&
                                    publicKey.isNotEmpty() && !loading
                                KvButton(
                                    text = "REQUEST SESSION",
                                    enabled = canRequest,
                                    onClick = {
                                        scope.launch {
                                            loading = true
                                            error = null
                                            try {
                                                val label = if (deviceName.isNotBlank()) deviceName.trim() else null
                                                val result = buildUnauthApi().createSessionRequest(
                                                    CreateSessionRequestBody(
                                                        label = label,
                                                        requestedDurationHours = selectedDuration.hours,
                                                    )
                                                )
                                                approvalUrl = result.url
                                                requestId = result.id
                                                qrBitmap = runCatching { urlToQrBitmap(result.url) }.getOrNull()
                                                phase = SetupPhase.WAITING
                                            } catch (e: Exception) {
                                                error = e.message ?: "Request failed"
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
                }

                SetupPhase.WAITING -> {
                    Text(
                        "> WAITING FOR APPROVAL",
                        fontFamily = PressStart2P,
                        fontSize = 9.sp,
                        color = KvDim,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(20.dp))

                    KvCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(15.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            KvSectionTitle("SCAN OR OPEN LINK")
                            Text(
                                "Show this QR code to an admin or send them the link below.",
                                fontFamily = VT323,
                                fontSize = 15.sp,
                                color = KvDim,
                                modifier = Modifier.padding(bottom = 14.dp),
                            )

                            qrBitmap?.let { bmp ->
                                Box(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .background(Color.White)
                                        .padding(8.dp),
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Approval QR code",
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            Text(
                                approvalUrl,
                                fontFamily = VT323,
                                fontSize = 12.sp,
                                color = Color(0xFF93c5fd),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        runCatching {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                Uri.parse(approvalUrl)
                                            )
                                            context.startActivity(intent)
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                            )

                            Spacer(Modifier.height(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = KvAccent,
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    "Polling for approval every 5s…",
                                    fontFamily = VT323,
                                    fontSize = 14.sp,
                                    color = KvDim,
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            KvButton(
                                text = "CANCEL",
                                onClick = {
                                    requestId = ""
                                    approvalUrl = ""
                                    qrBitmap = null
                                    error = null
                                    phase = SetupPhase.FORM
                                },
                            )
                        }
                    }
                }

                SetupPhase.REGISTERING -> {
                    Text(
                        "> REGISTERING…",
                        fontFamily = PressStart2P,
                        fontSize = 9.sp,
                        color = KvDim,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(40.dp))
                    CircularProgressIndicator(color = KvAccent)
                }
            }

            Spacer(Modifier.height(40.dp))
            Text("kv.osmosis.page", fontFamily = VT323, fontSize = 14.sp, color = KvFaint)
        }

        ScanlineOverlay()
    }
}
