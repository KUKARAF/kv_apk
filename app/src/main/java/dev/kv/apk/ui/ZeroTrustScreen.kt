package dev.kv.apk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.data.HardwareKeyItem
import dev.kv.apk.data.KvApi
import dev.kv.apk.ui.theme.KvAccent
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.KvInk
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.launch

@Composable
fun ZeroTrustScreen(
    api: KvApi,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var hwKeys by remember { mutableStateOf<List<HardwareKeyItem>>(emptyList()) }
    var hwLabel by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            try {
                hwKeys = api.listHardwareKeys()
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) onLogout()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) { load() }

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
            KvScreenHeader(title = "ZERO TRUST", onBack = onBack)
            Spacer(Modifier.height(12.dp))

            KvCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp)) {
                    Text(
                        "REGISTERED HARDWARE KEYS",
                        fontFamily = dev.kv.apk.ui.theme.PressStart2P,
                        fontSize = 8.sp,
                        color = dev.kv.apk.ui.theme.KvOrange,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Text(
                        "Register your FIDO2 key (YubiKey, etc.) before creating Zero Trust secrets. Each secret is bound to the key that encrypted it — only that key can decrypt it.",
                        fontFamily = VT323,
                        fontSize = 16.sp,
                        color = KvDim,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )

                    if (hwKeys.isEmpty()) {
                        Text("no hardware keys registered", fontFamily = VT323, fontSize = 15.sp, color = KvFaint)
                    } else {
                        hwKeys.forEach { key ->
                            HardwareKeyRow(
                                item = key,
                                onRemove = {
                                    scope.launch {
                                        try {
                                            api.deleteHardwareKey(key.id)
                                            toast = "removed ${key.label}"
                                            load()
                                        } catch (e: retrofit2.HttpException) {
                                            if (e.code() == 401) onLogout()
                                            else toast = "error ${e.code()}"
                                        } catch (e: Exception) {
                                            toast = e.message ?: "error"
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            KvCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp)) {
                    KvSectionTitle("REGISTER A NEW KEY", color = dev.kv.apk.ui.theme.KvOrange)

                    KvLabel("LABEL")
                    KvInput(
                        value = hwLabel,
                        onValueChange = { hwLabel = it },
                        placeholder = "YubiKey 5C",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                    )

                    KvButton(
                        text = "REGISTER KEY",
                        enabled = hwLabel.isNotBlank(),
                        onClick = {
                            toast = "FIDO2 registration not yet implemented"
                        },
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            KvToast(toast)
        }

        ScanlineOverlay()
    }
}

@Composable
private fun HardwareKeyRow(item: HardwareKeyItem, onRemove: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(KvFaint)
                .padding(bottom = 12.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.label,
                fontFamily = VT323,
                fontSize = 19.sp,
                color = KvInk,
                modifier = Modifier.weight(1f),
            )
            KvButtonDanger(text = "REMOVE", onClick = onRemove)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            "id ${item.credId}",
            fontFamily = VT323,
            fontSize = 15.sp,
            color = KvDim,
        )
        Text(
            "created ${item.createdAt}",
            fontFamily = VT323,
            fontSize = 15.sp,
            color = KvDim,
        )
        if (!item.lastUsed.isNullOrEmpty()) {
            Text(
                "last used ${item.lastUsed}",
                fontFamily = VT323,
                fontSize = 15.sp,
                color = KvDim,
            )
        }
    }
}
