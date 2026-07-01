package dev.kv.apk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.kv.apk.data.ApprovalItem
import dev.kv.apk.data.Prefs
import dev.kv.apk.data.buildApi
import dev.kv.apk.ui.ApprovalsScreen
import dev.kv.apk.ui.DevicesScreen
import dev.kv.apk.ui.HomeScreen
import dev.kv.apk.ui.HomeTile
import dev.kv.apk.ui.KeysScreen
import dev.kv.apk.ui.KvEntriesScreen
import dev.kv.apk.ui.RateLimitsScreen
import dev.kv.apk.ui.SecureShareScreen
import dev.kv.apk.ui.SessionScreen
import dev.kv.apk.ui.SessionRequestApprovalScreen
import dev.kv.apk.ui.SetupScreen
import dev.kv.apk.ui.ZeroTrustScreen
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvTheme
import kotlinx.coroutines.delay

private enum class AppScreen { SETUP, MAIN }

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
    }
}
