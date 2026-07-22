package dev.kv.apk

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.data.ApprovalItem
import dev.kv.apk.data.DeviceRegistrationRequest
import dev.kv.apk.data.Prefs
import dev.kv.apk.data.buildApi
import dev.kv.apk.ui.ApprovalsScreen
import dev.kv.apk.ui.DevicesScreen
import dev.kv.apk.ui.HomeScreen
import dev.kv.apk.ui.HomeTile
import dev.kv.apk.ui.KeysScreen
import dev.kv.apk.ui.KvEntriesScreen
import dev.kv.apk.ui.RateLimitsScreen
import dev.kv.apk.ui.RegisterDeviceDialog
import dev.kv.apk.ui.RegisterStep
import dev.kv.apk.ui.SecureShareScreen
import dev.kv.apk.ui.SessionScreen
import dev.kv.apk.ui.SessionRequestApprovalScreen
import dev.kv.apk.ui.SetupScreen
import dev.kv.apk.ui.ZeroTrustScreen
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDanger
import dev.kv.apk.ui.theme.KvTheme
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

private enum class AppScreen { SETUP, MAIN }

private fun generateAndStoreKeyPair(prefs: Prefs): String {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))
    val kp = kpg.generateKeyPair()
    prefs.devicePrivKeyPkcs8 = Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP)
    prefs.devicePubKeySpki = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
    prefs.deviceId = ""
    return prefs.devicePubKeySpki
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deepLinkId = intent.data
            ?.takeIf { it.scheme == "kvapp" && it.host == "session-request" }
            ?.getQueryParameter("id")

        setContent {
            KvTheme {
                val prefs = remember { Prefs(applicationContext) }
                var appScreen by remember {
                    mutableStateOf(
                        if (prefs.hasCredentials() && prefs.hasDeviceKey()) AppScreen.MAIN else AppScreen.SETUP
                    )
                }

                when (appScreen) {
                    AppScreen.SETUP -> SetupScreen(
                        prefs = prefs,
                        onSetupComplete = { appScreen = AppScreen.MAIN },
                    )
                    AppScreen.MAIN -> MainContent(
                        prefs = prefs,
                        initialDeepLinkId = deepLinkId,
                        onLogout = {
                            prefs.clearToken()
                            appScreen = AppScreen.SETUP
                        },
                        onTokenExpired = {
                            prefs.clearToken()
                            appScreen = AppScreen.SETUP
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainContent(
    prefs: Prefs,
    initialDeepLinkId: String? = null,
    onLogout: () -> Unit,
    onTokenExpired: () -> Unit,
) {
    val api = remember { buildApi(prefs.token) }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(if (initialDeepLinkId != null) "approve:$initialDeepLinkId" else "home") }

    var approvals by remember { mutableStateOf<List<ApprovalItem>>(emptyList()) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        try {
            approvals = api.listApprovals()
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) onTokenExpired()
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            refreshTick++
        }
    }

    // The app trusts the locally cached deviceId once it's set (see Prefs.hasDeviceKey),
    // so if the device was removed server-side (revoked in admin, DB reset, etc.) nothing
    // would otherwise tell the user — check once on entry and prompt to re-register.
    var deviceMissing by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var deviceRegisterStep by remember { mutableStateOf(RegisterStep.CHOOSE) }
    var deviceRegisterName by remember { mutableStateOf("") }
    var deviceRegisterBusy by remember { mutableStateOf(false) }
    var deviceRegisterError by remember { mutableStateOf("") }

    fun openDeviceDialog() {
        deviceRegisterStep = if (prefs.devicePrivKeyPkcs8.isNotBlank()) RegisterStep.CHOOSE else RegisterStep.NAME
        deviceRegisterName = ""
        deviceRegisterError = ""
        showDeviceDialog = true
    }

    LaunchedEffect(Unit) {
        try {
            val serverDevices = api.listDevices()
            val stillRegistered = prefs.deviceId.isNotBlank() && serverDevices.any { it.id == prefs.deviceId }
            if (!stillRegistered) {
                prefs.deviceId = ""
                deviceMissing = true
                openDeviceDialog()
            }
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) onTokenExpired()
        } catch (_: Exception) {}
    }

    BackHandler(enabled = screen != "home") { screen = "home" }

    val back: () -> Unit = { screen = "home" }

    Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(KvBg)
            .systemBarsPadding(),
    ) {
    when (screen) {
        "home" -> HomeScreen(
            sessionEmail = prefs.sessionEmail,
            tiles = listOf(
                HomeTile(
                    n = "01", title = "KV ENTRIES",
                    desc = "secrets manager",
                    onClick = { screen = "kv" },
                ),
                HomeTile(
                    n = "02", title = "API KEYS",
                    desc = "manage access keys",
                    onClick = { screen = "apikeys" },
                ),
                HomeTile(
                    n = "03", title = "APPROVALS",
                    desc = "${approvals.size} pending",
                    alert = approvals.isNotEmpty(),
                    onClick = { screen = "approvals" },
                ),
                HomeTile(
                    n = "04", title = "DEVICES",
                    desc = "registered devices",
                    onClick = { screen = "devices" },
                ),
                HomeTile(
                    n = "05", title = "ZERO TRUST",
                    desc = "fido2 hardware keys",
                    onClick = { screen = "zerotrust" },
                ),
                HomeTile(
                    n = "06", title = "RATE LIMITS",
                    desc = "blocked ips + log",
                    onClick = { screen = "ratelimits" },
                ),
                HomeTile(
                    n = "07", title = "SESSION",
                    desc = "signed in",
                    onClick = { screen = "session" },
                ),
                HomeTile(
                    n = "08", title = "SHARE",
                    desc = "one-time encrypted link",
                    onClick = { screen = "share" },
                ),
            ),
        )

        "kv" -> KvEntriesScreen(api = api, prefs = prefs, onBack = back, onLogout = onTokenExpired)

        "apikeys" -> KeysScreen(api = api, onBack = back, onLogout = onTokenExpired)

        "approvals" -> ApprovalsScreen(
            api = api,
            approvals = approvals,
            onBack = back,
            onLogout = onTokenExpired,
            onApprovalChanged = { refreshTick++ },
        )

        "devices" -> DevicesScreen(api = api, prefs = prefs, onBack = back, onLogout = onTokenExpired)

        "zerotrust" -> ZeroTrustScreen(api = api, onBack = back, onLogout = onTokenExpired)

        "ratelimits" -> RateLimitsScreen(api = api, onBack = back, onLogout = onTokenExpired)

        "session" -> SessionScreen(api = api, onBack = back, onLogout = onLogout)

        "share" -> SecureShareScreen(api = api, onBack = back, onLogout = onTokenExpired)

        else -> if (screen.startsWith("approve:")) {
            val requestId = screen.removePrefix("approve:")
            SessionRequestApprovalScreen(
                api = api,
                requestId = requestId,
                onDone = back,
                onLogout = onTokenExpired,
            )
        }
    }

    if (deviceMissing && !showDeviceDialog) {
        Row(
            modifier = androidx.compose.ui.Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(KvDanger.copy(alpha = 0.15f))
                .clickable { openDeviceDialog() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "This device isn't registered on the server — secrets can't be encrypted for it. Tap to fix.",
                fontFamily = VT323,
                fontSize = 15.sp,
                color = KvDanger,
                modifier = androidx.compose.ui.Modifier.weight(1f),
            )
        }
    }

    if (showDeviceDialog) {
        RegisterDeviceDialog(
            step = deviceRegisterStep,
            name = deviceRegisterName,
            busy = deviceRegisterBusy,
            error = deviceRegisterError,
            onNameChange = { deviceRegisterName = it },
            onChooseReuse = { deviceRegisterStep = RegisterStep.NAME },
            onChooseNew = {
                generateAndStoreKeyPair(prefs)
                deviceRegisterStep = RegisterStep.NAME
            },
            onConfirm = {
                scope.launch {
                    deviceRegisterBusy = true
                    deviceRegisterError = ""
                    try {
                        if (prefs.devicePrivKeyPkcs8.isBlank()) generateAndStoreKeyPair(prefs)
                        val resp = api.registerDevice(
                            DeviceRegistrationRequest(
                                name = deviceRegisterName.trim(),
                                publicKey = prefs.devicePubKeySpki,
                                keyType = "p256",
                            )
                        )
                        if (resp.isSuccessful) {
                            prefs.deviceId = resp.body()!!.id
                            deviceMissing = false
                            showDeviceDialog = false
                        } else {
                            deviceRegisterError = "registration failed: HTTP ${resp.code()}"
                        }
                    } catch (e: Exception) {
                        deviceRegisterError = e.message ?: "registration failed"
                    } finally {
                        deviceRegisterBusy = false
                    }
                }
            },
            onDismiss = { if (!deviceRegisterBusy) showDeviceDialog = false },
        )
    }
    }
}
