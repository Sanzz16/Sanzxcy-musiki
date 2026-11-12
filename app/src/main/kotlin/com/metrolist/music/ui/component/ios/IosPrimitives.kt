/**
 * Sanzxcy Music iOS-style primitives.
 * Original implementation; no Apple SF Symbols or proprietary assets are bundled.
 */
package com.metrolist.music.ui.component.ios

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.component.IosIndication
import com.metrolist.music.ui.component.glassBorder
import com.metrolist.music.ui.component.glassBlur
import com.metrolist.music.ui.theme.LocalLiquidGlass

private val IosPillShape = RoundedCornerShape(50)

@Composable
fun IosTabBar(
    selectedIndex: Int,
    labels: List<String>,
    icons: @Composable (index: Int, selected: Boolean) -> Unit,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalLiquidGlass.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(IosPillShape)
            .glassBlur(22.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (glass) .78f else 1f))
            .glassBorder(IosPillShape, glass)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val interactionSource = remember { MutableInteractionSource() }
            val scale by animateFloatAsState(
                if (selected) 1f else .96f,
                spring(dampingRatio = .82f, stiffness = Spring.StiffnessMedium),
                label = "tabScale",
            )
            val tint by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "tabTint",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(IosPillShape)
                    .then(if (selected) Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)) else Modifier)
                    .clickable(
                        role = Role.Button,
                        interactionSource = interactionSource,
                        indication = IosIndication,
                        onClick = { onSelected(index) },
                    )
                    .semantics { this.selected = selected }
                    .graphicsLayer { scaleX = scale; scaleY = scale },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    icons(index, selected)
                    Text(label, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun IosSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(IosPillShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f))
            .padding(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(IosPillShape)
                    .then(if (selected) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
                    .clickable(interactionSource = interactionSource, indication = IosIndication, onClick = { onSelected(index) })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(option, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
fun IosLoadingIndicator(modifier: Modifier = Modifier, size: Int = 22) {
    CircularProgressIndicator(modifier = modifier.size(size.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun IosPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(modifier = modifier.clickable(interactionSource = interactionSource, indication = IosIndication, onClick = onClick), contentAlignment = Alignment.Center) { content() }
}
