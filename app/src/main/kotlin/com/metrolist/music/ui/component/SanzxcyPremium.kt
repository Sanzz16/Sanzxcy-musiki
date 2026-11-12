package com.metrolist.music.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanzxcy.music.com.R
import com.metrolist.music.ui.theme.LocalLiquidGlass

val SanzxcyMint = Color(0xFF9DE4D1)
val SanzxcyMintStrong = Color(0xFF58C9AF)
val SanzxcyInk = Color(0xFF09100F)
val SanzxcyPanel = Color(0xFF111918)
val SanzxcyPanelRaised = Color(0xFF182421)

private val PremiumShape = RoundedCornerShape(24.dp)
private val CompactShape = RoundedCornerShape(16.dp)

@Composable
fun PremiumSurface(
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .975f else 1f,
        animationSpec = spring(dampingRatio = .82f, stiffness = Spring.StiffnessMedium),
        label = "premiumSurfacePress",
    )
    val shape = PremiumShape
    val base = if (raised) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
    val background = if (accent) {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .82f), base))
    } else Brush.linearGradient(listOf(base, base.copy(alpha = .92f)))
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(background)
            .glassBorder(shape, LocalLiquidGlass.current)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = source,
                    indication = IosIndication,
                    onClick = onClick,
                ) else Modifier,
            )
            .padding(16.dp),
    ) { content() }
}

@Composable
fun PremiumSectionHeader(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(interactionSource = source, indication = IosIndication, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (onClick != null) {
            Icon(painterResource(R.drawable.arrow_forward), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun PremiumPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: Int? = null,
    filled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .94f else 1f, spring(dampingRatio = .78f, stiffness = Spring.StiffnessMedium), label = "premiumButtonPress")
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f))
            .clickable(interactionSource = source, indication = IosIndication, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leadingIcon?.let { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(18.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun PremiumSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CompactShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)),
    )
}
