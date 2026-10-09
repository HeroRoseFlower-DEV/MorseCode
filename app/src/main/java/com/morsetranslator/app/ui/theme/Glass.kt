package com.morsetranslator.app.ui.theme

import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ------------------------------------------------------------------ tokens

/** True when the resolved theme is dark (respects the app's theme override). */
@Composable
fun isDarkGlass(): Boolean =
    MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** Translucent "frosted" container color for glass surfaces. */
@Composable
fun glassContainer(): Color =
    if (isDarkGlass()) Color(0xFF222B40).copy(alpha = 0.55f)
    else Color.White.copy(alpha = 0.62f)

/** Thin bright edge that sells the glass look. */
@Composable
fun glassBorder(): Color =
    if (isDarkGlass()) Color.White.copy(alpha = 0.16f)
    else Color.White.copy(alpha = 0.8f)

/** Strength of the top light streak. */
@Composable
fun glassSheenAlpha(): Float =
    if (isDarkGlass()) 0.10f else 0.16f

@Composable
fun glassGradientBrush(): Brush =
    Brush.horizontalGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        )
    )

// ------------------------------------------------------- aurora background

/**
 * Full-screen animated "aurora" backdrop: a soft gradient with three slowly
 * drifting, blurred color blobs. All screens sit on top of this.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = isDarkGlass()
    val base = if (dark) {
        Brush.verticalGradient(
            listOf(Color(0xFF0A0F22), Color(0xFF141B36), Color(0xFF0A0F22))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFFE9F1FF), Color(0xFFF5ECFF), Color(0xFFE9FBF2))
        )
    }
    Box(modifier = modifier.fillMaxSize().background(base)) {
        AuroraBlobs(dark)
        content()
    }
}

@Composable
private fun BoxScope.AuroraBlobs(dark: Boolean) {
    val t = rememberInfiniteTransition(label = "aurora")
    val dx1 by t.animateFloat(
        0f, 90f,
        infiniteRepeatable(tween(14000), RepeatMode.Reverse), label = "dx1"
    )
    val dy1 by t.animateFloat(
        0f, 60f,
        infiniteRepeatable(tween(11000), RepeatMode.Reverse), label = "dy1"
    )
    val dx2 by t.animateFloat(
        0f, -80f,
        infiniteRepeatable(tween(16000), RepeatMode.Reverse), label = "dx2"
    )
    val dy2 by t.animateFloat(
        0f, 70f,
        infiniteRepeatable(tween(13000), RepeatMode.Reverse), label = "dy2"
    )

    val colors = if (dark) {
        listOf(Color(0xFF6C5CE7), Color(0xFF00CEC9), Color(0xFFE17055))
    } else {
        listOf(Color(0xFFB9A7FF), Color(0xFF9BE8E4), Color(0xFFFFC9A8))
    }
    val alpha = if (dark) 0.5f else 0.7f
    val blurMod =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(70.dp)
        else Modifier

    Blob(
        Modifier.align(Alignment.TopStart).offset((-40).dp + dx1.dp, 30.dp + dy1.dp),
        colors[0], alpha, blurMod
    )
    Blob(
        Modifier.align(Alignment.CenterEnd).offset(60.dp + dx2.dp, (-40).dp + dy2.dp),
        colors[1], alpha, blurMod
    )
    Blob(
        Modifier.align(Alignment.BottomStart).offset(10.dp + dx1.dp, (-70).dp + dy2.dp),
        colors[2], alpha, blurMod
    )
}

@Composable
private fun Blob(modifier: Modifier, color: Color, alpha: Float, blurMod: Modifier) {
    Box(
        modifier
            .then(blurMod)
            .size(260.dp)
            .background(
                Brush.radialGradient(
                    listOf(color.copy(alpha = alpha), color.copy(alpha = 0f))
                ),
                CircleShape
            )
    )
}

// -------------------------------------------------------------- glass card

/**
 * Frosted-glass card: translucent container, bright thin border and a top
 * light streak for the liquid-glass refraction feel.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheen = glassSheenAlpha()
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Card(
        modifier = modifier
            .then(clickMod)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = sheen),
                        0.45f to Color.Transparent
                    ),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                )
            },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = glassContainer()),
        border = BorderStroke(1.dp, glassBorder()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

// ------------------------------------------------------------ glass buttons

/** Primary call-to-action: gradient pill with white content. */
@Composable
fun GlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val brush = if (enabled) glassGradientBrush()
    else Brush.horizontalGradient(listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.4f)))
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = Color.White)
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Round glass icon button, e.g. for playback controls. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = 56.dp
) {
    val container =
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        else glassContainer()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .border(1.dp, glassBorder(), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            else if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size * 0.44f)
        )
    }
}

/** Small glass chip for presets / filters. */
@Composable
fun GlassChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val container =
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        else glassContainer()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(1.dp, glassBorder(), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Segmented control: a glass pill track with a sliding gradient thumb.
 * [options] labels, [selected] index, [onSelect] callback.
 */
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(glassContainer())
            .border(1.dp, glassBorder(), RoundedCornerShape(20.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) glassGradientBrush() else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Small section heading used above glass cards. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 6.dp, bottom = 2.dp)
    )
}

/** Spacer height matching the floating nav, for scrollable columns. */
@Composable
fun GlassBottomSpacer() {
    androidx.compose.foundation.layout.Spacer(
        Modifier.height(104.dp)
    )
}
