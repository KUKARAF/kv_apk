package dev.kv.apk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import dev.kv.apk.R

val KvBg = Color(0xFF070B07)
val KvPanel = Color(0xFF0C120C)
val KvAccent = Color(0xFF79F279)
val KvOrange = Color(0xFFFFB13D)
val KvDanger = Color(0xFFFF6B6B)
val KvInk = Color(0xFFE6F0E6)
val KvDim = Color(0xFF728C72)
val KvFaint = Color(0xFF243024)

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

val PressStart2P = FontFamily(
    Font(
        googleFont = GoogleFont("Press Start 2P"),
        fontProvider = provider,
        weight = FontWeight.Normal,
        style = FontStyle.Normal,
    )
)

val VT323 = FontFamily(
    Font(
        googleFont = GoogleFont("VT323"),
        fontProvider = provider,
        weight = FontWeight.Normal,
        style = FontStyle.Normal,
    )
)

private val KvColorScheme = darkColorScheme(
    primary = KvAccent,
    onPrimary = Color(0xFF06210A),
    primaryContainer = KvFaint,
    onPrimaryContainer = KvAccent,
    secondary = KvOrange,
    onSecondary = Color(0xFF1A0F00),
    secondaryContainer = Color(0xFF2C1E00),
    onSecondaryContainer = KvOrange,
    tertiary = KvDim,
    onTertiary = KvBg,
    error = KvDanger,
    onError = Color(0xFF1A0000),
    errorContainer = Color(0xFF2C0000),
    onErrorContainer = KvDanger,
    background = KvBg,
    onBackground = KvInk,
    surface = KvPanel,
    onSurface = KvInk,
    onSurfaceVariant = KvDim,
    outline = KvFaint,
    outlineVariant = KvFaint,
    surfaceVariant = Color(0xFF0F160F),
    inverseSurface = KvInk,
    inverseOnSurface = KvBg,
)

@Composable
fun KvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KvColorScheme,
        content = content,
    )
}
