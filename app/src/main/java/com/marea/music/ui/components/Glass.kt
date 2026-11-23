package com.marea.music.ui.components

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.marea.music.ui.theme.MareaAccent
import com.marea.music.ui.theme.MareaAccentDeep
import com.marea.music.ui.theme.MareaBg
import com.marea.music.ui.theme.MareaCyan
import com.marea.music.ui.theme.MareaOnAccent
import com.marea.music.ui.theme.MareaText
import com.marea.music.ui.theme.MareaTextSecondary
import com.marea.music.ui.theme.MareaViolet

/** Si la cuenta tiene Premium. Lo usan los botones de descarga para mostrar el candado. */
val LocalIsPremium = compositionLocalOf { false }

/** Piezas del estilo cristal de Marea: superficies translúcidas, bordes con reflejo y brillos azules. */
object Glass {
    val fill = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.035f))
    )
    val fillStrong = Brush.linearGradient(
        listOf(Color(0xF0182D3F), Color(0xF00D1A24))
    )
    val border = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.30f),
            Color.White.copy(alpha = 0.05f),
            MareaAccent.copy(alpha = 0.30f)
        )
    )
    val accent = Brush.linearGradient(listOf(MareaCyan, MareaAccent, MareaAccentDeep))
    val accentHorizontal = Brush.horizontalGradient(listOf(MareaAccentDeep, MareaAccent, MareaCyan))
    val selected = Brush.linearGradient(
        listOf(MareaAccent.copy(alpha = 0.22f), MareaCyan.copy(alpha = 0.06f))
    )
}

/** Superficie de cristal: relleno translúcido + borde con reflejo. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(22.dp),
    strong: Boolean = false,
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .background(if (strong) Glass.fillStrong else Glass.fill, shape)
    .border(borderWidth, Glass.border, shape)

/** Halo de luz suave detrás del elemento (no recorta, así que el brillo sale por los bordes). */
fun Modifier.glow(color: Color = MareaAccent, spread: Dp = 28.dp, alpha: Float = 0.45f): Modifier =
    drawBehind {
        val radius = size.maxDimension / 2f + spread.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }

/** Fondo de toda la app: azul noche con luces difusas, como agua de noche. */
@Composable
fun MareaBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MareaText) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MareaBg)
            .drawBehind {
                fun orb(color: Color, x: Float, y: Float, r: Float, a: Float) {
                    val c = Offset(size.width * x, size.height * y)
                    val radius = size.minDimension * r
                    drawCircle(
                        Brush.radialGradient(listOf(color.copy(alpha = a), Color.Transparent), c, radius),
                        radius,
                        c
                    )
                }
                orb(MareaAccent, 0.05f, 0.02f, 0.95f, 0.30f)
                orb(MareaCyan, 1.0f, 0.38f, 0.70f, 0.12f)
                orb(MareaViolet, 0.15f, 0.92f, 0.85f, 0.20f)
            },
        content = content
    )
    }
}

/** La portada difuminada como fondo del reproductor. */
@Composable
fun ArtworkBackdrop(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxSize().background(MareaBg)) {
        if (url.isNotBlank()) {
            // En Android 12+ se difumina de verdad; en versiones anteriores la imagen pequeña
            // ampliada ya se ve suave.
            val request = remember(url) {
                ImageRequest.Builder(context).data(url).size(if (Build.VERSION.SDK_INT >= 31) 200 else 24).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }
                    .blur(70.dp)
                    .alpha(0.75f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MareaBg.copy(alpha = 0.35f),
                        0.45f to MareaBg.copy(alpha = 0.62f),
                        1f to MareaBg.copy(alpha = 0.97f)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val c = Offset(size.width * 0.5f, size.height * 0.28f)
                    val r = size.width * 0.9f
                    drawCircle(
                        Brush.radialGradient(listOf(MareaAccent.copy(alpha = 0.22f), Color.Transparent), c, r),
                        r,
                        c
                    )
                }
        )
    }
}

/** Tarjeta de cristal con relleno interno. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    strong: Boolean = false,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .glass(shape, strong)
            .padding(contentPadding),
        content = content
    )
}

/** Botón redondo de cristal para íconos. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = MareaText,
    active: Boolean = false,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) Glass.selected else Glass.fill, CircleShape)
            .border(1.dp, Glass.border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(iconSize)
        )
    }
}

/** Botón principal: píldora con degradado azul y brillo. */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .then(if (enabled) Modifier.glow(MareaAccent, spread = 6.dp, alpha = 0.35f) else Modifier)
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (enabled) Glass.accent else Brush.linearGradient(listOf(Color(0xFF22394C), Color(0xFF1A2E3E))),
                RoundedCornerShape(26.dp)
            )
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(26.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                color = MareaOnAccent,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MareaOnAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    color = if (enabled) MareaOnAccent else MareaTextSecondary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)
                )
            }
        }
    }
}

/** Píldora de cristal seleccionable (filtros, pestañas). */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Glass.selected else Glass.fill, shape)
            .border(1.dp, if (selected) Glass.accent else Glass.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) MareaCyan else MareaTextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = if (selected) MareaText else MareaTextSecondary,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

/** Etiqueta pequeña en mayúsculas con borde luminoso ("EN VIVO", "PREMIUM"...). */
@Composable
fun NeonTag(text: String, modifier: Modifier = Modifier, color: Color = MareaCyan) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** Título de sección con un acento luminoso a la izquierda. */
@Composable
fun GlassSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(if (subtitle != null) 34.dp else 20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(MareaCyan, MareaAccentDeep)))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MareaText
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MareaTextSecondary)
            }
        }
        trailing?.invoke()
    }
}

/** Portada con esquinas redondeadas y un ícono si no hay imagen. */
@Composable
fun CoverArt(
    url: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    requestSize: Int = 240,
    fallback: ImageVector = Icons.Rounded.MusicNote
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF1C3850), Color(0xFF0F2130))),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotBlank()) {
            val request = remember(url, requestSize) {
                ImageRequest.Builder(context).data(url).size(requestSize).crossfade(true).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(fallback, contentDescription = null, tint = MareaAccent.copy(alpha = 0.8f))
        }
    }
}

/** Barras animadas que indican la canción que está sonando. */
@Composable
fun NowPlayingBars(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    color: Color = MareaCyan,
    barWidth: Dp = 3.dp,
    height: Dp = 14.dp
) {
    val transition = rememberInfiniteTransition(label = "bars")
    val durations = listOf(520, 380, 610, 450)
    val levels = durations.map { d ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(d, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bar$d"
        )
    }
    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        levels.forEach { level ->
            val f = if (animate) level.value else 0.35f
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

/**
 * Barra de progreso con brillo. Arrastrar solo cambia la vista; el salto se hace al soltar
 * (antes se saltaba en cada movimiento del dedo).
 */
@Composable
fun GlowSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val duration = durationMs.coerceAtLeast(1L)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeek)
    val fraction = dragFraction ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shownMs = dragFraction?.let { (it * duration).toLong() } ?: positionMs

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .then(
                    if (enabled) Modifier
                        .pointerInputTap(duration) { f -> seek((f * duration).toLong()) }
                        .pointerInputDrag(
                            key = duration,
                            onDrag = { f -> dragFraction = f },
                            onEnd = {
                                dragFraction?.let { seek((it * duration).toLong()) }
                                dragFraction = null
                            }
                        )
                    else Modifier
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height / 2f
                val stroke = (if (dragFraction != null) 6.dp else 4.dp).toPx()
                val x = size.width * fraction
                drawLine(
                    color = Color.White.copy(alpha = 0.14f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = MareaAccent.copy(alpha = 0.28f),
                    start = Offset(0f, y),
                    end = Offset(x, y),
                    strokeWidth = stroke * 3.2f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(MareaAccentDeep, MareaAccent, MareaCyan),
                        startX = 0f,
                        endX = x.coerceAtLeast(1f)
                    ),
                    start = Offset(0f, y),
                    end = Offset(x, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                val haloR = 16.dp.toPx()
                drawCircle(
                    Brush.radialGradient(listOf(MareaCyan.copy(alpha = 0.55f), Color.Transparent), Offset(x, y), haloR),
                    haloR,
                    Offset(x, y)
                )
                drawCircle(Color.White, radius = (if (dragFraction != null) 8.dp else 6.dp).toPx(), center = Offset(x, y))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatDuration(shownMs / 1000L),
                style = MaterialTheme.typography.labelSmall,
                color = if (dragFraction != null) MareaCyan else MareaTextSecondary
            )
            Text(
                text = formatDuration(duration / 1000L),
                style = MaterialTheme.typography.labelSmall,
                color = MareaTextSecondary
            )
        }
    }
}

private fun Modifier.pointerInputTap(key: Any, onTap: (Float) -> Unit): Modifier =
    pointerInput(key) {
        detectTapGestures { offset -> onTap((offset.x / size.width).coerceIn(0f, 1f)) }
    }

private fun Modifier.pointerInputDrag(key: Any, onDrag: (Float) -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(key) {
        detectHorizontalDragGestures(
            onDragStart = { offset -> onDrag((offset.x / size.width).coerceIn(0f, 1f)) },
            onDragEnd = onEnd,
            onDragCancel = onEnd,
            onHorizontalDrag = { change, _ ->
                change.consume()
                onDrag((change.position.x / size.width).coerceIn(0f, 1f))
            }
        )
    }

/** Difumina los bordes superior e inferior de una lista (para la letra). */
fun Modifier.fadingEdges(top: Float = 0.12f, bottom: Float = 0.12f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                top to Color.Black,
                (1f - bottom) to Color.Black,
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }

/** Colores de campos de texto sobre cristal. */
@Composable
fun glassFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White.copy(alpha = 0.07f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
    disabledContainerColor = Color.White.copy(alpha = 0.03f),
    focusedBorderColor = MareaAccent,
    unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
    cursorColor = MareaCyan,
    focusedTextColor = MareaText,
    unfocusedTextColor = MareaText,
    focusedPlaceholderColor = MareaTextSecondary,
    unfocusedPlaceholderColor = MareaTextSecondary,
    focusedLabelColor = MareaCyan,
    unfocusedLabelColor = MareaTextSecondary,
    focusedLeadingIconColor = MareaCyan,
    unfocusedLeadingIconColor = MareaTextSecondary,
    focusedTrailingIconColor = MareaCyan,
    unfocusedTrailingIconColor = MareaTextSecondary
)
