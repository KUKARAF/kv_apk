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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import dev.kv.apk.data.DeviceItem
import dev.kv.apk.data.DeviceRegistrationRequest
import dev.kv.apk.data.KvApi
import dev.kv.apk.data.Prefs
import dev.kv.apk.ui.theme.KvAccent
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDanger
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.KvInk
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.launch
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

private enum class RegisterStep { CHOOSE, NAME }

private fun generateAndStoreKeyPair(prefs: Prefs): String {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))
    val kp = kpg.generateKeyPair()
    prefs.devicePrivKeyPkcs8 = Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP)
    prefs.devicePubKeySpki = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
    prefs.deviceId = ""
    return prefs.devicePubKeySpki
}

@Composable
fun DevicesScreen(
    api: KvApi,
    prefs: Prefs,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    var toast by remember { mutableStateOf("") }

    var showRegisterDialog by remember { mutableStateOf(false) }
    var registerStep by remember { mutableStateOf(RegisterStep.CHOOSE) }
    var registerName by remember { mutableStateOf("") }
    var registerBusy by remember { mutableStateOf(false) }
    var registerError by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            try {
                devices = api.listDevices()
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
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                KvScreenHeader(
                    title = "DEVICES",
                    onBack = onBack,
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KvButtonOutline(
                                text = "REGISTER",
                                onClick = {
                                    registerStep = if (prefs.devicePrivKeyPkcs8.isNotBlank())
                                        RegisterStep.CHOOSE else RegisterStep.NAME
                                    registerName = ""
                                    registerError = ""
                                    showRegisterDialog = true
                                },
                                color = KvAccent,
                            )
                            KvButtonOutline(
                                text = "REFRESH",
                                onClick = { load() },
                                color = KvDim,
                            )
                        }
                    },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("NAME", fontFamily = PressStart2P, fontSize = 8.sp, color = KvDim)
                Text("REGISTERED", fontFamily = PressStart2P, fontSize = 8.sp, color = KvDim)
            }

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("no devices registered", fontFamily = VT323, fontSize = 16.sp, color = KvDim)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceRow(
                            device = device,
                            onDelete = {
                                scope.launch {
                                    try {
                                        api.deleteDevice(device.id)
                                        toast = "deleted ${device.name}"
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
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            KvToast(toast)
        }

        ScanlineOverlay()

        if (showRegisterDialog) {
            RegisterDeviceDialog(
                step = registerStep,
                name = registerName,
                busy = registerBusy,
                error = registerError,
                onNameChange = { registerName = it },
                onChooseReuse = { registerStep = RegisterStep.NAME },
                onChooseNew = {
                    generateAndStoreKeyPair(prefs)
                    registerStep = RegisterStep.NAME
                },
                onConfirm = {
                    scope.launch {
                        registerBusy = true
                        registerError = ""
                        try {
                            if (prefs.devicePrivKeyPkcs8.isBlank()) generateAndStoreKeyPair(prefs)
                            val resp = api.registerDevice(
                                DeviceRegistrationRequest(
                                    name = registerName.trim(),
                                    publicKey = prefs.devicePubKeySpki,
                                    keyType = "p256",
                                )
                            )
                            if (resp.isSuccessful) {
                                prefs.deviceId = resp.body()!!.id
                                toast = "device registered"
                                showRegisterDialog = false
                                load()
                            } else {
                                registerError = "registration failed: HTTP ${resp.code()}"
                            }
                        } catch (e: Exception) {
                            registerError = e.message ?: "registration failed"
                        } finally {
                            registerBusy = false
                        }
                    }
                },
                onDismiss = { if (!registerBusy) showRegisterDialog = false },
            )
        }
    }
}

@Composable
private fun RegisterDeviceDialog(
    step: RegisterStep,
    name: String,
    busy: Boolean,
    error: String,
    onNameChange: (String) -> Unit,
    onChooseReuse: () -> Unit,
    onChooseNew: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "REGISTER THIS DEVICE",
                fontFamily = PressStart2P,
                fontSize = 9.sp,
                color = KvAccent,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    RegisterStep.CHOOSE -> {
                        Text(
                            "A key pair already exists on this device. Use the existing key or generate a new one?",
                            fontFamily = VT323,
                            fontSize = 16.sp,
                            color = KvDim,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            KvButton(
                                text = "RE-REGISTER EXISTING KEY",
                                onClick = onChooseReuse,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            KvButtonOutline(
                                text = "GENERATE NEW KEY PAIR",
                                onClick = onChooseNew,
                                modifier = Modifier.fillMaxWidth(),
                                color = KvDanger,
                            )
                        }
                    }
                    RegisterStep.NAME -> {
                        Text(
                            "DEVICE NAME",
                            fontFamily = PressStart2P,
                            fontSize = 7.sp,
                            color = KvDim,
                        )
                        KvInput(
                            value = name,
                            onValueChange = onNameChange,
                            placeholder = "pixel pro",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (error.isNotBlank()) {
                    Text(error, fontFamily = VT323, fontSize = 15.sp, color = KvDanger)
                }
            }
        },
        confirmButton = {
            if (step == RegisterStep.NAME) {
                KvButton(
                    text = if (busy) "…" else "REGISTER",
                    onClick = onConfirm,
                    enabled = !busy && name.isNotBlank(),
                )
            }
        },
        dismissButton = {
            if (step == RegisterStep.NAME) {
                KvButtonOutline(
                    text = "CANCEL",
                    onClick = onDismiss,
                    enabled = !busy,
                )
            }
        },
        containerColor = Color(0xFF0C120C),
        titleContentColor = KvAccent,
        textContentColor = KvInk,
    )
}

@Composable
private fun DeviceRow(device: DeviceItem, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(device.name, fontFamily = VT323, fontSize = 19.sp, color = KvInk)
            Text(device.registeredAt, fontFamily = VT323, fontSize = 15.sp, color = KvDim)
        }
        KvButtonDanger(text = "DELETE", onClick = onDelete)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KvFaint),
    )
}
