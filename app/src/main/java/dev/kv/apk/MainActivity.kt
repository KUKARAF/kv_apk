package dev.kv.apk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import dev.kv.apk.data.Prefs
import dev.kv.apk.ui.ApprovalsScreen
import dev.kv.apk.ui.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val prefs = remember { Prefs(applicationContext) }
                var hasCredentials by remember { mutableStateOf(prefs.hasCredentials()) }

                if (hasCredentials) {
                    ApprovalsScreen(
                        prefs = prefs,
                        onLogout = {
                            prefs.clear()
                            hasCredentials = false
                        }
                    )
                } else {
                    SetupScreen(
                        prefs = prefs,
                        onSaved = { hasCredentials = true }
                    )
                }
            }
        }
    }
}
