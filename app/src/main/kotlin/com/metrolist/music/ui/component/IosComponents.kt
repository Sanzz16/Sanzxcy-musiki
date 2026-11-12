/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Sanzxcy Music: komponen dasar bergaya iOS 26 (switch, alert dialog, efek sentuh).
 * Dipasang menggantikan komponen Material di seluruh aplikasi.
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.metrolist.music.ui.theme.LocalLiquidGlass
import kotlinx.coroutines.launch

// ───────────── Efek sentuh iOS: sorotan lembut, tanpa riak Material ─────────────

object IosIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = IosIndicationNode(interactionSource)

    override fun hashCode(): Int = -7

    override fun equals(other: Any?): Boolean = other === this
}

private class IosIndicationNode(
    private val source: InteractionSource,
) : androidx.compose.ui.Modifier.Node(), DrawModifierNode {
    private val level = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> level.animateTo(1f, tween(60))
                    is PressInteraction.Release -> level.animateTo(0f, tween(240))
                    is PressInteraction.Cancel -> level.animateTo(0f, tween(240))
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = level.value
        if (a > 0.001f) drawRect(Color(0xFF8E8E93).copy(alpha = 0.26f * a))
    }
}

// ───────────── Switch iOS (51x31, hijau, thumb putih membal) ─────────────

@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
        label = "iosSwitch",
    )
    val on = Color(0xFF67D9BA)
    val off = MaterialTheme.colorScheme.surfaceContainerHighest
    val track = lerp(off, on, progress.coerceIn(0f, 1f))
    val source = interactionSource ?: remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .size(width = 51.dp, height = 31.dp)
                .clip(CircleShape)
                .background(track.copy(alpha = if (enabled) 1f else 0.72f))
                .then(
                    if (onCheckedChange != null) {
                        Modifier.toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Switch,
                            interactionSource = source,
                            indication = null,
                            onValueChange = onCheckedChange,
                        )
                    } else {
                        Modifier
                    },
                ).padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = 20.dp * progress)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

// ───────────── Alert dialog iOS (judul tengah, garis tipis, tombol sejajar) ─────────────

@Composable
fun IosAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(26.dp),
    containerColor: Color = Color.Unspecified,
    iconContentColor: Color = Color.Unspecified,
    titleContentColor: Color = Color.Unspecified,
    textContentColor: Color = Color.Unspecified,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    val glass = LocalLiquidGlass.current
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        GlassWindowBlur(radiusPx = 32)
        val base = if (containerColor == Color.Unspecified) MaterialTheme.colorScheme.surfaceContainerHigh else containerColor
        Surface(
            modifier = modifier.widthIn(max = 340.dp).glassBorder(shape, glass),
            shape = shape,
            color = base.copy(alpha = if (glass) 0.92f else 1f),
            tonalElevation = tonalElevation,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    icon?.invoke()
                    if (title != null) {
                        ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)) { title() }
                    }
                    if (text != null) {
                        Box(Modifier.padding(top = if (title != null) 8.dp else 0.dp)) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center)) { text() }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 50.dp)) {
                    if (dismissButton != null) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { dismissButton() }
                        VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { confirmButton() }
                }
            }
        }
    }
}
