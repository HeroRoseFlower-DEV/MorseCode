package com.morsetranslator.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Calm, light-first design system.
 *
 * Warm ivory surfaces, navy/slate text, muted turquoise primary, restrained
 * gold secondary, neutral grey borders. No neon, no heavy blur, no
 * always-running decoration — static, high-contrast, readable.
 */

// ------------------------------------------------------------ text helpers

/**
 * Wraps text in Unicode isolates (LRI…PDI) so Morse/dot-dash sequences keep
 * their LTR order inside RTL (e.g. Persian) layouts without manually
 * reversing anything.
 */
fun String.ltrIsolate(): String = "\u2066$this\u2069"

/** Text style forcing LTR direction for Morse code, even on RTL screens. */
@Composable
fun ltrMorseStyle(base: TextStyle = MaterialTheme.typography.bodyLarge): TextStyle =
    base.copy(textDirection = TextDirection.Ltr)

/** Monospace LTR text for Morse sequences. */
@Composable
fun MorseText(
    morse: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified
) {
    Text(
        text = morse.ltrIsolate(),
        modifier = modifier,
        style = ltrMorseStyle(
            style.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        ),
        color = color
    )
}

// ------------------------------------------------------------------ card

/** Standard calm card: solid surface, neutral border, 16dp radius. */
@Composable
fun CalmCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Card(
        modifier = modifier.then(clickMod),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

// ---------------------------------------------------------------- buttons

/** Primary call-to-action: muted turquoise fill. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) Icon(icon, contentDescription = null)
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Round tonal icon button with a 48dp minimum touch target. */
@Composable
fun CalmIconButton(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = 48.dp
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

/** Small pill chip for presets and filters. */
@Composable
fun CalmChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Chip with a visible outline, for horizontally scrolling preset rows. */
@Composable
fun CalmOutlineChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    androidx.compose.material3.AssistChip(
        onClick = onClick,
        label = { Text(text) },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            labelColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    )
}

// ------------------------------------------------------- segmented control

/** Two/three-way segmented control with a calm selected pill. */
@Composable
fun CalmSegmentedCard(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** When set, option i gets the test tag "$testTagPrefix$i" (UI tests). */
    testTagPrefix: String? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(index) }
                        .then(
                            if (testTagPrefix != null) Modifier.testTag("$testTagPrefix$index")
                            else Modifier
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ misc

/** Small section heading. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

/** Calm colors for outlined text fields. */
@Composable
fun calmTextFieldColors(): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        cursorColor = MaterialTheme.colorScheme.primary
    )

/** Spacer clearing the bottom navigation bar in scrollable columns. */
@Composable
fun BottomSpacer() {
    Spacer(Modifier.height(96.dp))
}
