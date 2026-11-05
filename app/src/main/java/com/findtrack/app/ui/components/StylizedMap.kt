package com.findtrack.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.findtrack.app.R
import com.findtrack.app.ble.DeviceIconType
import com.findtrack.app.ui.theme.CardWhite
import com.findtrack.app.ui.theme.GreenPrimary
import com.findtrack.app.ui.theme.MapLand
import com.findtrack.app.ui.theme.MapLabel
import com.findtrack.app.ui.theme.MapPark
import com.findtrack.app.ui.theme.MapRoad
import com.findtrack.app.ui.theme.MapRoadCasing
import com.findtrack.app.ui.theme.MapWater
import com.findtrack.app.ui.theme.TextPrimary
import com.findtrack.app.ui.theme.TextSecondary
import com.findtrack.app.ui.theme.UserBlue
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A device marker rendered on the stylized map. */
data class MapPin(
    val address: String,
    val iconType: DeviceIconType,
    val distanceM: Double,
    val selected: Boolean,
    val calloutTitle: String? = null,
    val calloutSubtitle: String? = null,
    /** Current RSSI, used to color the pin's anchor by signal quality. */
    val rssi: Int? = null,
    /** Real GPS coordinates of the device when known (from History). */
    val lat: Double? = null,
    val lon: Double? = null
) {
    /** Google Maps coordinate if a real fix exists. */
    val realLatLng: com.google.android.gms.maps.model.LatLng?
        get() = if (lat != null && lon != null) {
            com.google.android.gms.maps.model.LatLng(lat, lon)
        } else null
}

/**
 * The offline stylized vector map from the approved design: land, river,
 * parks, street grid and labels are drawn procedurally, so the app renders
 * this map instantly with zero network access and zero API keys.
 *
 * Device pins are placed at a bearing derived from a stable hash of the
 * device address, at a radius proportional to the estimated distance — i.e.
 * distances shown are real (RSSI-derived), bearings are illustrative.
 */
@Composable
fun StylizedMap(
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    showControls: Boolean = true,
    showLegend: Boolean = true,
    userLabel: String? = null,
    onZoomIn: (() -> Unit)? = null,
    onZoomOut: (() -> Unit)? = null,
    onRecenter: (() -> Unit)? = null,
    onPinTap: (String) -> Unit = {}
) {
    val zoomClamped = zoom.coerceIn(0.5f, 2.5f)
    val labelSizePx = with(LocalDensity.current) { 11.sp.toPx() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MapLand)
    ) {
        // ------------------------------------------------------------ map
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRect(color = MapLand)

            // River hugging the right edge (matches the design's Riverside).
            val river = Path().apply {
                moveTo(w * 0.80f, -20f)
                cubicTo(w * 0.70f, h * 0.28f, w * 0.98f, h * 0.58f, w * 0.84f, h + 20f)
                lineTo(w + 30f, h + 20f)
                lineTo(w + 30f, -20f)
                close()
            }
            drawPath(river, MapWater)

            // Parks (top-left "Riverside Park" + small south garden).
            val parkTop = Path().apply {
                moveTo(-10f, h * 0.04f)
                lineTo(w * 0.34f, h * 0.015f)
                lineTo(w * 0.40f, h * 0.20f)
                lineTo(w * 0.22f, h * 0.30f)
                lineTo(-10f, h * 0.24f)
                close()
            }
            drawPath(parkTop, MapPark)

            val parkBottom = Path().apply {
                moveTo(-10f, h * 0.80f)
                lineTo(w * 0.24f, h * 0.86f)
                lineTo(w * 0.18f, h + 10f)
                lineTo(-10f, h + 10f)
                close()
            }
            drawPath(parkBottom, MapPark)

            // Street grid — casing pass then road pass for crisp edges.
            val spacing = 74.dp.toPx() * zoomClamped
            val gridOffset = (w / 2f) % spacing
            val roads = mutableListOf<Pair<Offset, Offset>>()
            var x = gridOffset
            while (x < w) {
                roads.add(Offset(x, -20f) to Offset(x, h + 20f))
                x += spacing
            }
            var y = (h / 2f) % (spacing * 1.15f)
            while (y < h) {
                roads.add(Offset(-20f, y) to Offset(w + 20f, y))
                y += spacing * 1.15f
            }
            // Two named diagonal avenues: Riverside Dr and Maple Ave.
            roads.add(Offset(-20f, h * 0.18f) to Offset(w * 0.82f, h * 0.72f))
            roads.add(Offset(w * 0.10f, h + 20f) to Offset(w * 0.86f, h * 0.30f))

            roads.forEach { (a, b) ->
                drawLine(MapRoadCasing, a, b, strokeWidth = 15.dp.toPx(), cap = StrokeCap.Round)
            }
            roads.forEach { (a, b) ->
                drawLine(MapRoad, a, b, strokeWidth = 11.dp.toPx(), cap = StrokeCap.Round)
            }

            // Labels
            val paint = android.graphics.Paint().apply {
                color = 0xFF8CA096.toInt()
                textSize = labelSizePx
                isAntiAlias = true
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            drawContext.canvas.nativeCanvas.apply {
                drawText("Riverside Park", w * 0.05f, h * 0.12f, paint)
                drawText("Riverside Dr", w * 0.42f, h * 0.46f, paint)
                drawText("Maple Ave", w * 0.20f, h * 0.74f, paint)
            }
        }

        // ------------------------------------------------------- user dot
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val density = LocalDensity.current

            val pulse = rememberInfiniteTransition(label = "userPulse")
            val pulseAlpha by pulse.animateFloat(
                initialValue = 0.18f,
                targetValue = 0.04f,
                animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
                label = "userPulseAlpha"
            )
            val pulseScale by pulse.animateFloat(
                initialValue = 1f,
                targetValue = 1.7f,
                animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
                label = "userPulseScale"
            )

            // Accuracy halo + blue dot ("you are here"), design-accurate.
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (0.5f * wPx - 56.dp.toPx() * pulseScale).roundToInt(),
                            (0.52f * hPx - 56.dp.toPx() * pulseScale).roundToInt()
                        )
                    }
                    .size(112.dp)
                    .graphicsAlpha(pulseAlpha)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(color = UserBlue.copy(alpha = 0.16f), radius = size.minDimension / 2f)
                }
            }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset((0.5f * wPx - 11.dp.toPx()).roundToInt(), (0.52f * hPx - 11.dp.toPx()).roundToInt())
                    }
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .zIndex(3f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(UserBlue)
                )
            }

            // "You" chip under the blue dot — the phone is always blue.
            Text(
                text = stringResource(R.string.map_you),
                style = MaterialTheme.typography.labelSmall,
                color = UserBlue,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (0.5f * wPx - 26.dp.toPx()).roundToInt(),
                            (0.52f * hPx + 15.dp.toPx()).roundToInt()
                        )
                    }
                    .width(52.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(vertical = 3.dp)
                    .zIndex(3f)
            )

            // ---------------------------------------------------- device pins
            pins.forEach { pin ->
                val bearing = stableBearing(pin.address)
                val radiusFraction =
                    (pin.distanceM / 120.0).toFloat().coerceIn(0.07f, 0.40f) * zoomClamped
                val angle = Math.toRadians(bearing.toDouble())
                val fx = (0.5f + (radiusFraction * cos(angle)).toFloat()).coerceIn(0.08f, 0.92f)
                val fy = (0.52f + (radiusFraction * sin(angle) * 0.88f).toFloat()).coerceIn(0.09f, 0.90f)

                val bubbleSize = if (pin.selected) 52.dp else 40.dp

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (fx * wPx - bubbleSize.toPx() / 2f).roundToInt(),
                                (fy * hPx - bubbleSize.toPx() / 2f).roundToInt()
                            )
                        }
                        .zIndex(if (pin.selected) 2f else 1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onPinTap(pin.address) }
                ) {
                    if (pin.selected) {
                        // Soft pulsing ring behind the selected pin.
                        val ringPulse = rememberInfiniteTransition(label = "pinPulse")
                        val ringScale by ringPulse.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.9f,
                            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
                            label = "pinPulseScale"
                        )
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (bubbleSize.toPx() * (1f - ringScale) / 2f).roundToInt(),
                                        (bubbleSize.toPx() * (1f - ringScale) / 2f).roundToInt()
                                    )
                                }
                                .size(bubbleSize)
                                .graphicsScale(ringScale)
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawCircle(color = GreenPrimary.copy(alpha = 0.14f), radius = size.minDimension / 2f)
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = if (pin.selected) GreenPrimary else CardWhite,
                            shadowElevation = 3.dp,
                            modifier = Modifier.size(bubbleSize)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = deviceIcon(pin.iconType),
                                    contentDescription = pin.calloutTitle ?: "Device",
                                    tint = if (pin.selected) Color.White else signalColor(pin.rssi),
                                    modifier = Modifier.size(bubbleSize * 0.46f)
                                )
                            }
                        }

                        // Anchor dot: marks the exact spot and carries the live
                        // signal color (green / amber / red).
                        Box(
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (pin.selected) GreenPrimary else signalColor(pin.rssi))
                        )
                    }

                    // Callout card above the pin (Finder Map design element).
                    val callout = pin.calloutTitle
                    if (callout != null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset { IntOffset(0, (-64).dp.roundToPx()) }
                                .shadow(4.dp, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardWhite)
                                .clickable { onPinTap(pin.address) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .widthIn(max = 180.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = callout,
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            pin.calloutSubtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // ------------------------------------------ user location caption
            userLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.85f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // -------------------------------------------------------- legend
            if (showLegend) {
                MapLegend(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                )
            }

            // ------------------------------------------------------ controls
            MapOverlayControls(
                showControls = showControls,
                onRecenter = onRecenter,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut
            )
        }
    }
}

@Composable
private fun MapControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(CardWhite)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Compact map key: blue dot = this phone, green marker = a Bluetooth device.
 * Shared by the stylized map and the Google Maps surface.
 */
@Composable
fun MapLegend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .shadow(3.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite.copy(alpha = 0.95f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LegendRow(color = UserBlue, label = stringResource(R.string.map_legend_you))
        LegendRow(color = GreenPrimary, label = stringResource(R.string.map_legend_device))
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            modifier = Modifier.padding(start = 7.dp)
        )
    }
}

/** Stable illustrative bearing (0..360) derived from the device address. */
fun stableBearing(address: String): Int {    var hash = 0
    for (c in address) hash = 31 * hash + c.code
    return (hash % 360 + 360) % 360
}

private fun Modifier.graphicsAlpha(alpha: Float): Modifier =
    this.graphicsLayer { this.alpha = alpha }

private fun Modifier.graphicsScale(scale: Float): Modifier =
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

/**
 * Shared zoom + recenter controls overlay (used by both the Google map and
 * the stylized offline map).
 */
@Composable
fun MapOverlayControls(
    showControls: Boolean,
    onRecenter: (() -> Unit)?,
    onZoomIn: (() -> Unit)?,
    onZoomOut: (() -> Unit)?
) {
    if (!showControls) return
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MapControlButton(Icons.Outlined.MyLocation, "Recenter") { onRecenter?.invoke() }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MapControlButton(Icons.Outlined.Add, "Zoom in") { onZoomIn?.invoke() }
                MapControlButton(Icons.Outlined.Remove, "Zoom out") { onZoomOut?.invoke() }
            }
        }
    }
}
