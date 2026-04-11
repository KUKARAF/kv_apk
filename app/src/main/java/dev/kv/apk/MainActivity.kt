package dev.kv.apk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import dev.kv.apk.data.Prefs
import dev.kv.apk.ui.ApprovalsScreen
import dev.kv.apk.ui.QrScannerScreen
import dev.kv.apk.ui.SetupScreen

private enum class Screen { SETUP, SCANNING, APPROVALS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val prefs = remember { Prefs(applicationContext) }
                var screen by remember {
                    mutableStateOf(if (prefs.hasCredentials()) Screen.APPROVALS else Screen.SETUP)
                }

                when (screen) {
                    Screen.SETUP -> SetupScreen(
                        onScanQr = { screen = Screen.SCANNING },
                        onSaved = { token ->
                            prefs.token = token
                            screen = Screen.APPROVALS
                        },
                    )
                    Screen.SCANNING -> QrScannerScreen(
                        onScanned = { token ->
                            prefs.token = token.trim()
                            screen = Screen.APPROVALS
                        },
                        onCancel = { screen = Screen.SETUP },
                    )
                    Screen.APPROVALS -> ApprovalsScreen(
                        prefs = prefs,
                        onLogout = {
                            prefs.clear()
                            screen = Screen.SETUP
                        },
                    )
                }
            }
        }
    }
}
