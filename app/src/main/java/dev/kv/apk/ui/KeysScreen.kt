package dev.kv.apk.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.kv.apk.data.ApiKeyItem
import dev.kv.apk.data.CreateApiKeyRequest
import dev.kv.apk.data.KvApi
import dev.kv.apk.data.ScopeRule
import dev.kv.apk.ui.theme.KvAccent
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.KvInk
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.launch

@Composable
fun KeysScreen(
    api: KvApi,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var keys by remember { mutableStateOf<List<ApiKeyItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }

    var formLabel by remember { mutableStateOf("") }
    var formType by remember { mutableStateOf("Standard") }
    var formExpires by remember { mutableStateOf("") }
    var formScope by remember { mutableStateOf("") }

    fun loadKeys() {
        scope.launch {
            loading = true
            try {
                keys = api.listKeys()
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) onLogout()
                else toast = "error ${e.code()}"
            } catch (e: Exception) {
                toast = e.message ?: "error"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadKeys() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KvBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                KvScreenHeader(title = "API KEYS", onBack = onBack)
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                item {
                    KvCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            KvSectionTitle("CREATE KEY")

                            KvLabel("LABEL")
                            KvInput(
                                value = formLabel,
                                onValueChange = { formLabel = it },
                                placeholder = "thinkpad deploy key",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 13.dp),
                            )

                            KvLabel("TYPE")
                            Row(
                                modifier = Modifier.padding(bottom = 13.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf("Standard", "Approval", "Read-only").forEach { t ->
                                    val selected = formType == t
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (selected) KvAccent.copy(alpha = 0.12f) else KvFaint,
                                                RoundedCornerShape(3.dp),
                                            )
                                            .border(
                                                1.dp,
                                                if (selected) KvAccent.copy(alpha = 0.3f) else KvFaint,
                                                RoundedCornerShape(3.dp),
                                            )
                                            .clickable { formType = t }
                                            .padding(horizontal = 7.dp, vertical = 5.dp),
                                    ) {
                                        Text(
                                            t,
                                            fontFamily = PressStart2P,
                                            fontSize = 7.sp,
                                            color = if (selected) KvAccent else KvDim,
                                        )
                                    }
                                }
                            }

                            KvLabel("EXPIRES AT (OPTIONAL)")
                            KvInput(
                                value = formExpires,
                                onValueChange = { formExpires = it },
                                placeholder = "mm / dd / yyyy",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 13.dp),
                            )

                            KvLabel("SCOPE")
                            KvInput(
                                value = formScope,
                                onValueChange = { formScope = it },
                                placeholder = "osmosis/deployment",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 15.dp),
                            )

                            KvButton(
                                text = "CREATE",
                                enabled = formLabel.isNotBlank() && !loading,
                                onClick = {
                                    scope.launch {
                                        try {
                                            val scopes = if (formScope.isNotBlank())
                                                listOf(ScopeRule(formScope.trim(), listOf("read", "write")))
                                            else emptyList()
                                            api.createKey(
                                                CreateApiKeyRequest(
                                                    label = formLabel.trim(),
                                                    type = formType.lowercase(),
                                                    expiresAt = formExpires.ifBlank { null },
                                                    scopes = scopes,
                                                )
                                            )
                                            toast = "created ${formLabel.trim()}"
                                            formLabel = ""
                                            formExpires = ""
                                            formScope = ""
                                            loadKeys()
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
                    Spacer(Modifier.height(16.dp))
                }

                items(keys, key = { it.id }) { key ->
                    KeyCard(
                        item = key,
                        onRevoke = {
                            scope.launch {
                                try {
                                    api.revokeKey(key.id)
                                    toast = "revoked ${key.label}"
                                    loadKeys()
                                } catch (e: retrofit2.HttpException) {
                                    if (e.code() == 401) onLogout()
                                    else toast = "error ${e.code()}"
                                } catch (e: Exception) {
                                    toast = e.message ?: "error"
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            KvToast(toast)
        }

        ScanlineOverlay()
    }
}

@Composable
private fun KeyCard(item: ApiKeyItem, onRevoke: () -> Unit) {
    val active = item.status.lowercase() == "active"
    KvCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    item.label,
                    fontFamily = VT323,
                    fontSize = 18.sp,
                    color = KvInk,
                    modifier = Modifier.weight(1f),
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.width(10.dp))
                KvStatusChip(if (active) "ACTIVE" else "REVOKED", active)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("type ${item.keyType}", fontFamily = VT323, fontSize = 15.sp, color = KvDim)
                if (item.scopes.isNotEmpty()) {
                    Text(
                        "scope ${item.scopes.joinToString { it.scope }}",
                        fontFamily = VT323,
                        fontSize = 15.sp,
                        color = KvDim,
                    )
                }
            }

            if (!item.expiresAt.isNullOrEmpty()) {
                Text(
                    "expires ${item.expiresAt}",
                    fontFamily = VT323,
                    fontSize = 14.sp,
                    color = KvDim,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            if (active) {
                KvButtonDanger(text = "REVOKE", onClick = onRevoke)
            }
        }
    }
}
