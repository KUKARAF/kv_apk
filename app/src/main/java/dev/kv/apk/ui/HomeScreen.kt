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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kv.apk.ui.theme.KvAccent
import dev.kv.apk.ui.theme.KvBg
import dev.kv.apk.ui.theme.KvDim
import dev.kv.apk.ui.theme.KvFaint
import dev.kv.apk.ui.theme.KvInk
import dev.kv.apk.ui.theme.KvOrange
import dev.kv.apk.ui.theme.KvPanel
import dev.kv.apk.ui.theme.PressStart2P
import dev.kv.apk.ui.theme.VT323

data class HomeTile(
    val n: String,
    val title: String,
    val desc: String,
    val alert: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun HomeScreen(
    sessionEmail: String,
    tiles: List<HomeTile>,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KvBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        "KV·VAULT",
                        fontFamily = PressStart2P,
                        fontSize = 18.sp,
                        color = KvAccent,
                    )
                    Text(
                        "secrets manager // mobile",
                        fontFamily = VT323,
                        fontSize = 16.sp,
                        color = KvDim,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("v0.4", fontFamily = PressStart2P, fontSize = 7.sp, color = KvDim)
                    Text("kv.osmosis", fontFamily = PressStart2P, fontSize = 7.sp, color = KvFaint)
                }
            }

            Spacer(Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KvPanel, RoundedCornerShape(3.dp))
                    .border(1.dp, KvFaint, RoundedCornerShape(3.dp))
                    .padding(13.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(KvAccent, 8.dp)
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(
                            "SESSION ACTIVE",
                            fontFamily = PressStart2P,
                            fontSize = 7.sp,
                            color = KvDim,
                        )
                        Text(
                            sessionEmail.ifEmpty { "signed in" },
                            fontFamily = VT323,
                            fontSize = 18.sp,
                            color = KvInk,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("> SELECT MODULE", fontFamily = PressStart2P, fontSize = 8.sp, color = KvDim)
                BlinkingCursor()
            }

            Spacer(Modifier.height(12.dp))

            val rows = tiles.chunked(2)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { tile ->
                        HomeTileCard(tile, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "— end of modules —",
                fontFamily = VT323,
                fontSize = 14.sp,
                color = KvFaint,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        ScanlineOverlay()
    }
}

@Composable
private fun HomeTileCard(tile: HomeTile, modifier: Modifier = Modifier) {
    val bracketStroke = 1.dp
    val bracketSize = 8.dp

    Box(
        modifier = modifier
            .height(112.dp)
            .background(KvPanel, RoundedCornerShape(3.dp))
            .border(1.dp, KvFaint, RoundedCornerShape(3.dp))
            .drawBehind {
                val bs = bracketSize.toPx()
                val inset = 6.dp.toPx()
                val s = bracketStroke.toPx()
                val c = KvAccent.copy(alpha = 0.55f)
                drawLine(c, Offset(inset, inset), Offset(inset + bs, inset), s)
                drawLine(c, Offset(inset, inset), Offset(inset, inset + bs), s)
                drawLine(c, Offset(size.width - inset - bs, size.height - inset), Offset(size.width - inset, size.height - inset), s)
                drawLine(c, Offset(size.width - inset, size.height - inset - bs), Offset(size.width - inset, size.height - inset), s)
            }
            .clickable { tile.onClick() }
            .padding(horizontal = 12.dp, vertical = 13.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tile.n, fontFamily = PressStart2P, fontSize = 9.sp, color = KvFaint)
                if (tile.alert) {
                    PulsingDot(KvOrange, 7.dp)
                }
            }
            Text(
                tile.title,
                fontFamily = PressStart2P,
                fontSize = 10.sp,
                color = KvAccent,
                lineHeight = 15.sp,
            )
            Text(
                tile.desc,
                fontFamily = VT323,
                fontSize = 15.sp,
                color = KvDim,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
