package dev.kv.apk.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.data.CreateShareRequest
import dev.kv.apk.data.KvApi
import dev.kv.apk.ui.theme.KvAccent
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

private sealed class ShareUiState {
    object Idle : ShareUiState()
    object Loading : ShareUiState()
    data class Done(val url: String) : ShareUiState()
}

private val TTL_OPTIONS = listOf(
    "1H" to 1.0,
    "24H" to 24.0,
    "7D" to 168.0,
    "30D" to 720.0,
)

@Composable
fun SecureShareScreen(
    api: KvApi,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var secretValue by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var selectedTtl by remember { mutableStateOf(24.0) }
    var uiState by remember { mutableStateOf<ShareUiState>(ShareUiState.Idle) }
    var toast by remember { mutableStateOf("") }

    fun reset() {
        secretValue = ""
        label = ""
        selectedTtl = 24.0
        uiState = ShareUiState.Idle
    }

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
            KvScreenHeader(title = "SECURE SHARE", onBack = onBack)
            Spacer(Modifier.height(14.dp))

            when (val state = uiState) {
                is ShareUiState.Idle, is ShareUiState.Loading -> {
                    KvCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            KvSectionTitle("SECRET VALUE")
                            KvInput(
                                value = secretValue,
                                onValueChange = { secretValue = it },
                                placeholder = "Enter the secret to share…",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp),
                                singleLine = false,
                                maxLines = 8,
                                fontSize = 17,
                            )

                            KvLabel("LABEL  (shown to recipient)")
                            KvInput(
                                value = label,
                                onValueChange = { label = it },
                                placeholder = "e.g. DATABASE_URL",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp),
                            )

                            KvLabel("EXPIRES IN")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TTL_OPTIONS.forEach { (label, hours) ->
                                    val selected = selectedTtl == hours
                                    KvButtonOutline(
                                        text = label,
                                        color = if (selected) KvAccent else KvDim,
                                        onClick = { selectedTtl = hours },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            if (uiState is ShareUiState.Loading) {
                                Text(
                                    "> encrypting…",
                                    fontFamily = VT323,
                                    fontSize = 16.sp,
                                    color = KvAccent,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }

                            KvButton(
                                text = "ENCRYPT & GENERATE LINK",
                                enabled = secretValue.isNotBlank() && uiState !is ShareUiState.Loading,
                                onClick = {
                                    scope.launch {
                                        uiState = ShareUiState.Loading
                                        try {
                                            val rng = SecureRandom()
                                            val aesKey = ByteArray(32).also { rng.nextBytes(it) }
                                            val nonce = ByteArray(12).also { rng.nextBytes(it) }
                                            val ct = aesGcmEncryptShare(
                                                key = aesKey,
                                                nonce = nonce,
                                                plaintext = secretValue.toByteArray(Charsets.UTF_8),
                                            )
                                            val flags = Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
                                            val nonceB64 = Base64.encodeToString(nonce, flags)
                                            val ctB64 = Base64.encodeToString(ct, flags)
                                            val keyB64 = Base64.encodeToString(aesKey, flags)

                                            val resp = api.createShare(
                                                CreateShareRequest(
                                                    kvKey = label.trim().ifBlank { "shared-secret" },
                                                    ciphertext = ctB64,
                                                    nonce = nonceB64,
                                                    expiresInHours = selectedTtl,
                                                )
                                            )
                                            val url = "https://kv.osmosis.page/share.html?id=${resp.id}#key=${keyB64}"
                                            uiState = ShareUiState.Done(url)
                                        } catch (e: retrofit2.HttpException) {
                                            if (e.code() == 401) onLogout()
                                            else {
                                                toast = "error ${e.code()}"
                                                uiState = ShareUiState.Idle
                                            }
                                        } catch (e: Exception) {
                                            toast = e.message ?: "error"
                                            uiState = ShareUiState.Idle
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                is ShareUiState.Done -> {
                    KvCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            KvSectionTitle("LINK READY")
                            Text(
                                "One-time link — consumed when opened or after TTL expires.",
                                fontFamily = VT323,
                                fontSize = 15.sp,
                                color = KvDim,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(bottom = 14.dp),
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, KvFaint, RoundedCornerShape(4.dp))
                                    .padding(10.dp)
                                    .padding(bottom = 4.dp),
                            ) {
                                SelectionContainer {
                                    Text(
                                        state.url,
                                        fontFamily = VT323,
                                        fontSize = 14.sp,
                                        color = KvAccent,
                                        lineHeight = 17.sp,
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            KvButton(
                                text = "COPY LINK",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("share link", state.url))
                                    toast = "copied"
                                },
                            )

                            KvButtonOutline(
                                text = "SHARE VIA…",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, state.url)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share secure link"))
                                },
                            )

                            KvButtonOutline(
                                text = "NEW SHARE",
                                color = KvDim,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { reset() },
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            KvToast(toast)
        }

        ScanlineOverlay()
    }
}

private fun aesGcmEncryptShare(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    return cipher.doFinal(plaintext)
}
