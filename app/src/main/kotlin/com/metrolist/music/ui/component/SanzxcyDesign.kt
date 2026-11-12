/**
 * Sanzxcy Music design system — dark glass, mint accents and editorial hierarchy.
 * These primitives are deliberately content-agnostic so every feature screen can keep its data/actions
 * while adopting the same Sanzxcy visual language.
 */
package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.ui.theme.LocalLiquidGlass

@Composable
fun Modifier.sanzxcyAmbientBackground(): Modifier {
    val base = MaterialTheme.colorScheme.background
    val mint = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    return this
        .background(
            Brush.verticalGradient(
                0f to base,
                0.52f to base.copy(alpha = 0.985f),
                1f to base,
            ),
        )
        .drawBehind {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(mint.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.06f),
                    radius = size.minDimension * 0.72f,
                ),
                radius = size.minDimension * 0.72f,
                center = Offset(size.width * 0.92f, size.height * 0.06f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondary.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width * 0.05f, size.height * 0.56f),
                    radius = size.minDimension * 0.68f,
                ),
                radius = size.minDimension * 0.68f,
                center = Offset(size.width * 0.05f, size.height * 0.56f),
            )
        }
}

@Composable
fun SanzxcyPageIntro(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.2.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            trailingContent()
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SanzxcySectionHeading(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(width = 4.dp, height = 27.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SanzxcyGlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    innerPadding: Dp = 16.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalLiquidGlass.current
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .glassBlur(radius = 24.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (glass) 0.76f else 1f),
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (glass) 0.58f else 1f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = if (glass) 0.34f else 0.16f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
                        MaterialTheme.colorScheme.primary.copy(alpha = if (glass) 0.34f else 0.11f),
                    ),
                ),
                shape = shape,
            )
            .drawBehind {
                // Fine upper-edge reflection gives the panel depth without reducing text contrast.
                drawLine(
                    color = Color.White.copy(alpha = if (glass) 0.16f else 0.05f),
                    start = Offset(cornerRadius.toPx(), 0f),
                    end = Offset(size.width - cornerRadius.toPx(), 0f),
                    strokeWidth = 1.2.dp.toPx(),
                )
            }
            .padding(innerPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

@Composable
fun SanzxcyEyebrowPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}


@Composable
fun SanzxcyStatusIndicator(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.52f))
            .border(1.dp, accent.copy(alpha = if (active) 0.30f else 0.14f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 0.92f else 0.70f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun SanzxcySectionRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}


/** Compact, accessible glass action surface for existing screen actions. */
@Composable
fun SanzxcyGlassAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = if (enabled) 0.22f else 0.08f),
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Small uppercase tag used to distinguish a page's current collection context. */
@Composable
fun SanzxcyContextTag(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(5.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/** Small contextual notice for existing screens; contains no feature-specific state. */
@Composable
fun SanzxcyGlassNotice(
    message: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.48f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f), shape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(6.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
