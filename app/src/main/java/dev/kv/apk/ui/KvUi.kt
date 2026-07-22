package dev.kv.apk.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.ui.theme.KvAccent
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDanger
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.KvInk
import dev.kv.apk.ui.theme.KvOrange
import dev.kv.apk.ui.theme.KvPanel
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323

@Composable
fun KvCard(
    modifier: Modifier = Modifier,
    cornerColor: Color = KvAccent,
    cornerAlpha: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(KvPanel, RoundedCornerShape(3.dp))
            .border(1.dp, KvFaint, RoundedCornerShape(3.dp))
            .drawBehind {
                val bracketSize = 9.dp.toPx()
                val inset = 7.dp.toPx()
                val stroke = 1.dp.toPx()
                val c = cornerColor.copy(alpha = cornerAlpha)
                // top-left
                drawLine(c, Offset(inset, inset), Offset(inset + bracketSize, inset), stroke)
                drawLine(c, Offset(inset, inset), Offset(inset, inset + bracketSize), stroke)
                // bottom-right
                drawLine(c, Offset(size.width - inset - bracketSize, size.height - inset), Offset(size.width - inset, size.height - inset), stroke)
                drawLine(c, Offset(size.width - inset, size.height - inset - bracketSize), Offset(size.width - inset, size.height - inset), stroke)
            },
        content = content,
    )
}

@Composable
fun KvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KvAccent,
            contentColor = Color(0xFF06210A),
            disabledContainerColor = KvFaint,
            disabledContentColor = KvDim,
        ),
    ) {
        Text(text, fontFamily = PressStart2P, fontSize = 9.sp, letterSpacing = 0.06.sp)
    }
}

@Composable
fun KvButtonDanger(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = KvDanger.copy(alpha = 0.08f),
            contentColor = KvDanger,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, KvDanger),
    ) {
        Text(text, fontFamily = PressStart2P, fontSize = 8.sp)
    }
}

@Composable
fun KvButtonOutline(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = KvAccent,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = color,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, color),
    ) {
        Text(text, fontFamily = PressStart2P, fontSize = 8.sp)
    }
}

@Composable
fun KvInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    fontSize: Int = 18,
    enabled: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = modifier
            .background(if (enabled) Color(0xFF070D07) else Color(0xFF0A0A0A), RoundedCornerShape(2.dp))
            .border(1.dp, KvFaint, RoundedCornerShape(2.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(KvAccent),
        textStyle = TextStyle(
            color = if (enabled) KvInk else KvDim,
            fontFamily = VT323,
            fontSize = fontSize.sp,
        ),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(placeholder, color = Color(0xFF3F5840), fontFamily = VT323, fontSize = fontSize.sp)
            }
            inner()
        },
    )
}

@Composable
fun KvLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(bottom = 6.dp),
        fontFamily = PressStart2P,
        fontSize = 8.sp,
        color = KvDim,
        letterSpacing = 0.06.sp,
    )
}

@Composable
fun KvSectionTitle(text: String, color: Color = KvOrange, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(bottom = 14.dp),
        fontFamily = PressStart2P,
        fontSize = 8.sp,
        color = color,
        letterSpacing = 0.06.sp,
    )
}

@Composable
fun KvScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KvButtonOutline(text = "<", onClick = onBack, modifier = Modifier.height(38.dp))
        Spacer(Modifier.width(11.dp))
        Text(
            title,
            fontFamily = PressStart2P,
            fontSize = 13.sp,
            color = KvAccent,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun KvChip(
    text: String,
    color: Color = KvAccent,
) {
    Text(
        text,
        fontFamily = PressStart2P,
        fontSize = 7.sp,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
    )
}

@Composable
fun KvStatusChip(text: String, active: Boolean) {
    val color = if (active) KvAccent else KvDanger
    Text(
        text,
        fontFamily = PressStart2P,
        fontSize = 7.sp,
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
fun PulsingDot(color: Color = KvAccent, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )
    Box(
        modifier = Modifier
            .size(size)
            .alpha(alpha)
            .background(color, CircleShape),
    )
}

@Composable
fun BlinkingCursor(color: Color = KvAccent) {
    val transition = rememberInfiniteTransition(label = "blink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1100
                1f at 0
                1f at 549
                0f at 550
                0f at 1099
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink_alpha",
    )
    Text("_", color = color.copy(alpha = alpha), fontFamily = VT323, fontSize = 18.sp)
}

@Composable
fun ScanlineOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                var y = 0f
                while (y < size.height) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.22f),
                        topLeft = Offset(0f, y + 2f),
                        size = Size(size.width, 1f),
                    )
                    y += 3f
                }
            },
    )
}

@Composable
fun KvToast(message: String) {
    if (message.isEmpty()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 38.dp)
            .background(Color(0xFF0C160C), RoundedCornerShape(3.dp))
            .border(1.dp, KvAccent, RoundedCornerShape(3.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("> $message", fontFamily = VT323, fontSize = 17.sp, color = KvAccent, modifier = Modifier.weight(1f))
            BlinkingCursor()
        }
    }
}

@Composable
fun KvCheckbox(
    checked: Boolean,
    label: String,
    onToggle: () -> Unit,
    color: Color = KvAccent,
) {
    Row(
        modifier = Modifier
            .clickable { onToggle() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(Color(0xFF070D07))
                .border(1.dp, KvFaint)
                .then(
                    if (checked) Modifier.drawBehind {
                        drawRect(
                            color = color,
                            topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                            size = Size(12.dp.toPx(), 12.dp.toPx()),
                        )
                    } else Modifier
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            fontFamily = VT323,
            fontSize = 17.sp,
            color = KvInk,
        )
    }
}

@Composable
fun KvDividerRow(leftLabel: String, rightLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .drawBehind {
                drawLine(KvFaint, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(bottom = 8.dp),
    ) {
        Text(leftLabel, fontFamily = PressStart2P, fontSize = 8.sp, color = KvDim, modifier = Modifier.weight(1f))
        Text(rightLabel, fontFamily = PressStart2P, fontSize = 8.sp, color = KvDim)
    }
}

enum class RegisterStep { CHOOSE, NAME }

@Composable
fun RegisterDeviceDialog(
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
