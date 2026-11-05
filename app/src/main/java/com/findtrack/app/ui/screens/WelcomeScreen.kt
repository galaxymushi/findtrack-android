package com.findtrack.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.findtrack.app.R
import com.findtrack.app.ui.theme.GreenPrimary
import com.findtrack.app.ui.theme.UserBlue
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val WelcomeTop = Color(0xFF0B3D25)
private val WelcomeMid = Color(0xFF155E38)
private val WelcomeEnd = Color(0xFF23864C)
private val MapPaper = Color(0xFFEFF3F0)

/**
 * First-launch welcome: a full-bleed green gradient with three animated pages
 * (scanning, map markers, privacy). Finishing or skipping marks onboarding as
 * done in DataStore; Settings can replay it.
 */
@Composable
fun WelcomeScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val pages = listOf(
        WelcomePage(R.string.welcome_title_1, R.string.welcome_body_1),
        WelcomePage(R.string.welcome_title_2, R.string.welcome_body_2),
        WelcomePage(R.string.welcome_title_3, R.string.welcome_body_3)
    )
    val isLast = pagerState.currentPage == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(WelcomeTop, WelcomeMid, WelcomeEnd))
            )
    ) {
        DecorativeRings()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // ------------------------------------------------------- header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Place,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(start = 10.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onFinish) {
                    Text(
                        text = stringResource(R.string.welcome_skip),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // -------------------------------------------------------- pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) { page ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.height(250.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (page) {
                            0 -> RadarArt()
                            1 -> MarkerArt()
                            else -> PrivacyArt()
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = stringResource(pages[page].title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(pages[page].body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }

            // ---------------------------------------------------- indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    val active = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(if (active) 26.dp else 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (active) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                    )
                }
            }

            // -------------------------------------------------------- CTA
            Button(
                onClick = {
                    if (isLast) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = GreenPrimary
                )
            ) {
                Text(
                    text = stringResource(
                        if (isLast) R.string.welcome_start else R.string.welcome_next
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private data class WelcomePage(@StringRes val title: Int, @StringRes val body: Int)

/** Soft concentric rings + glows behind the content. */
@Composable
private fun DecorativeRings() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = Color.White.copy(alpha = 0.05f),
            radius = w * 0.75f,
            center = Offset(w * 0.9f, h * 0.06f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.05f),
            radius = w * 0.55f,
            center = Offset(w * 0.05f, h * 0.92f)
        )
    }
}

/** Page 1 — a radar sweep with the device at its center. */
@Composable
private fun RadarArt() {
    val transition = rememberInfiniteTransition(label = "welcomeRadar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1500, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(modifier = Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = Color.White.copy(alpha = 0.10f), radius = radius * 0.98f)
            listOf(0.74f, 0.48f, 0.24f).forEach { fraction ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f),
                    radius = radius * fraction,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            rotate(sweep) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.38f)
                        ),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 70f,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                )
            }
            // Small blips on the radar.
            listOf(210f to 0.62f, 305f to 0.42f, 120f to 0.78f).forEach { (angle, fraction) ->
                val rad = Math.toRadians(angle.toDouble())
                drawCircle(
                    color = Color.White.copy(alpha = 0.55f),
                    radius = 4.dp.toPx(),
                    center = Offset(
                        center.x + (cos(rad) * radius * fraction).toFloat(),
                        center.y + (sin(rad) * radius * fraction).toFloat()
                    )
                )
            }
        }
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Color.White)
                .zIndex(2f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = GreenPrimary,
                modifier = Modifier.size(44.dp)
            )
        }
        // Soft pulse ring around the hero.
        Box(
            modifier = Modifier
                .size(96.dp)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.35f),
                    shape = CircleShape
                )
                .padding(10.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = (pulse - 0.7f) * 0.6f),
                    radius = size.minDimension / 2f
                )
            }
        }
    }
}

/** Page 2 — mini map showing the blue "you" dot apart from a green device pin. */
@Composable
private fun MarkerArt() {
    val transition = rememberInfiniteTransition(label = "welcomeMarker")
    val halo by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "halo"
    )

    Box(
        modifier = Modifier
            .width(250.dp)
            .height(196.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MapPaper)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Roads.
            drawLine(
                color = Color.White,
                start = Offset(-10f, size.height * 0.28f),
                end = Offset(size.width + 10f, size.height * 0.72f),
                strokeWidth = 22.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(size.width * 0.55f, -10f),
                end = Offset(size.width * 0.35f, size.height + 10f),
                strokeWidth = 16.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Park block.
            drawRoundRect(
                color = Color(0xFFD9EBD3),
                topLeft = Offset(size.width * 0.66f, size.height * 0.60f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height * 0.30f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx())
            )
            // Dotted connection between phone and device.
            drawLine(
                color = GreenPrimary.copy(alpha = 0.35f),
                start = Offset(size.width * 0.30f, size.height * 0.62f),
                end = Offset(size.width * 0.70f, size.height * 0.33f),
                strokeWidth = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()

            // Phone marker: blue dot with white ring + pulsing halo.
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (w * 0.30f - (18.dp.toPx() * halo)).roundToInt(),
                            (h * 0.62f - (18.dp.toPx() * halo)).roundToInt()
                        )
                    }
                    .size((36.dp * halo))
                    .clip(CircleShape)
                    .background(UserBlue.copy(alpha = 0.18f))
            )
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (w * 0.30f - 11.dp.toPx()).roundToInt(),
                            (h * 0.62f - 11.dp.toPx()).roundToInt()
                        )
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
            Chip(
                text = stringResource(R.string.map_you),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (w * 0.30f - 30.dp.toPx()).roundToInt(),
                            (h * 0.62f + 16.dp.toPx()).roundToInt()
                        )
                    }
                    .width(60.dp),
                color = UserBlue
            )

            // Device marker: green pin with an anchor dot underneath.
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (w * 0.70f - 18.dp.toPx()).roundToInt(),
                            (h * 0.33f - 18.dp.toPx()).roundToInt()
                        )
                    }
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GreenPrimary)
                    .zIndex(2f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Speaker,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (w * 0.70f - 3.dp.toPx()).roundToInt(),
                            (h * 0.33f + 21.dp.toPx()).roundToInt()
                        )
                    }
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(GreenPrimary)
            )
            Chip(
                text = stringResource(R.string.map_legend_device),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (w * 0.70f - 58.dp.toPx()).roundToInt(),
                            (h * 0.33f + 32.dp.toPx()).roundToInt()
                        )
                    }
                    .width(116.dp),
                color = GreenPrimary
            )
        }
    }
}

/** Page 3 — an offline phone with a lock. */
@Composable
private fun PrivacyArt() {
    Box(modifier = Modifier.size(190.dp, 232.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(30.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(2.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                listOf("On-device", "No account", "Works offline").forEach { label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 18.dp, y = (-14).dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = GreenPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** Small rounded label used by the marker illustration. */
@Composable
private fun Chip(text: String, modifier: Modifier = Modifier, color: Color) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
