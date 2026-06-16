package dev.kv.apk.ui

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
import dev.kv.apk.data.DeviceItem
import dev.kv.apk.data.KvApi
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvInk
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.launch

@Composable
fun DevicesScreen(
    api: KvApi,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<DeviceItem>>(emptyList()) }
    var toast by remember { mutableStateOf("") }

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
                        KvButtonOutline(
                            text = "REFRESH",
                            onClick = { load() },
                            color = dev.kv.apk.ui.theme.KvDim,
                        )
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
    }
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
            .background(dev.kv.apk.ui.theme.KvFaint),
    )
}
