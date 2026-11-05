package com.findtrack.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.findtrack.app.ble.SignalLevel
import com.findtrack.app.ui.theme.ChipDefaultBg
import com.findtrack.app.ui.theme.GreenPrimary
import com.findtrack.app.ui.theme.SignalMedium
import com.findtrack.app.ui.theme.SignalWeak
import kotlin.math.roundToInt

/** Signal color used by map anchors and charts: green → amber → warm red. */
fun signalColor(rssi: Int?): Color = when {
    rssi == null -> GreenPrimary
    rssi >= -67 -> GreenPrimary
    rssi >= -80 -> SignalMedium
    else -> SignalWeak
}

fun signalColor(level: SignalLevel?): Color = when (level) {
    SignalLevel.VERY_STRONG, SignalLevel.STRONG -> GreenPrimary
    SignalLevel.MODERATE -> SignalMedium
    SignalLevel.WEAK -> SignalWeak
    null -> GreenPrimary
}

/** Compact 4-bar signal meter; lit bars follow the current signal level. */
@Composable
fun SignalBars(
    level: SignalLevel?,
    modifier: Modifier = Modifier
) {
    val active = when (level) {
        SignalLevel.VERY_STRONG -> 4
        SignalLevel.STRONG -> 3
        SignalLevel.MODERATE -> 2
        SignalLevel.WEAK -> 1
        null -> 0
    }
    val color = signalColor(level)
    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((5 + index * 4).dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (index < active) color else ChipDefaultBg)
            )
        }
    }
}

/**
 * Rolling RSSI chart used on the device detail screen. Newest reading is on
 * the right; the line color follows the latest signal quality.
 */
@Composable
fun SignalSparkline(
    points: List<Int>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val min = points.min()
        val max = points.max()
        val range = (max - min).coerceAtLeast(6)
        val color = signalColor(points.last())
        val stepX = size.width / (points.size - 1).toFloat()
        val topInset = size.height * 0.12f
        val usable = size.height - topInset * 2

        val line = Path()
        var lastY = size.height / 2f
        points.forEachIndexed { index, value ->
            val normalized = (value - min).toFloat() / range
            val x = index * stepX
            val y = size.height - topInset - normalized * usable
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
            lastY = y
        }

        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.20f), Color.Transparent)
            )
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawCircle(
            color = color,
            radius = 3.5.dp.toPx(),
            center = Offset(size.width, lastY)
        )
    }
}

/** Average of the last [window] readings, rounded; null when empty. */
fun averageRssi(points: List<Int>, window: Int = 10): Int? {
    if (points.isEmpty()) return null
    return points.takeLast(window).average().roundToInt()
}
