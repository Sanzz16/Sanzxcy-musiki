/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Sanzxcy Music: efek Liquid Glass (blur latar belakang) tanpa library tambahan.
 *
 * Cara kerja: konten layar utama direkam sekali ke satu GraphicsLayer ("source").
 * Komponen kaca (nav bar, mini player) menggambar ulang layer itu di area kecilnya
 * dengan RenderEffect blur. Hanya area kecil yang diburamkan, jadi ringan.
 * Perangkat lama / RAM kecil otomatis memakai permukaan translusen tanpa blur.
 */

package com.metrolist.music.ui.component

import android.app.ActivityManager
import android.content.Context
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.theme.LocalLiquidGlass
import kotlin.math.roundToInt

class GlassBackdrop internal constructor(
    internal val layer: GraphicsLayer,
) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

private data class GlassBlurProfile(
    val enabled: Boolean,
    val radiusScale: Float,
    val useLens: Boolean,
)

private val NoGlassBlur = GlassBlurProfile(enabled = false, radiusScale = 0f, useLens = false)

/** Adaptasi kualitas GPU: permukaan kaca tetap tampil di semua Android, blur dibuat lebih ringan di device menengah. */
private fun getGlassBlurProfile(context: Context): GlassBlurProfile {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return NoGlassBlur
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return NoGlassBlur
    if (activityManager.isLowRamDevice) return NoGlassBlur

    val cores = Runtime.getRuntime().availableProcessors()
    val memoryClass = activityManager.memoryClass
    if (cores < 6 || memoryClass < 192) return NoGlassBlur

    val powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    if (powerSave) return GlassBlurProfile(enabled = true, radiusScale = 0.20f, useLens = false)

    return if (cores >= 8 && memoryClass >= 256) {
        GlassBlurProfile(
            enabled = true,
            radiusScale = 0.56f,
            useLens = Build.VERSION.SDK_INT >= 33,
        )
    } else {
        GlassBlurProfile(enabled = true, radiusScale = 0.35f, useLens = false)
    }
}

@Composable
private fun rememberGlassBlurProfile(): GlassBlurProfile {
    val glass = LocalLiquidGlass.current
    val context = LocalContext.current
    return remember(glass, context) { if (glass) getGlassBlurProfile(context) else NoGlassBlur }
}

@Composable
private fun rememberBlurActive(): Boolean = rememberGlassBlurProfile().enabled

/** Lensa kaca: tepi membiaskan latar (membesar sedikit) dengan aberasi warna halus. Android 13+. */
private const val LENS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
half4 main(float2 coord) {
    float2 c = coord - size * 0.5;
    float2 q = abs(c) - (size * 0.5 - float2(radius, radius));
    float d = length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float range = max(radius, 1.0);
    float t = clamp(-d / range, 0.0, 1.0);
    float bend = (1.0 - t) * (1.0 - t) * 14.0;
    float2 dir = normalize(c + float2(0.0001, 0.0001));
    float2 uv = coord - dir * bend;
    half4 base = content.eval(uv);
    half r = content.eval(uv + dir * 1.6 * (1.0 - t)).r;
    half b = content.eval(uv - dir * 1.6 * (1.0 - t)).b;
    return half4(r, base.g, b, base.a);
}
"""

@RequiresApi(Build.VERSION_CODES.S)
private fun buildGlassEffect(
    blurPx: Float,
    w: Float,
    h: Float,
    cornerPx: Float,
    useLens: Boolean,
): androidx.compose.ui.graphics.RenderEffect {
    val blur = AndroidRenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
    if (Build.VERSION.SDK_INT >= 33 && useLens) {
        val result =
            runCatching {
                val shader = RuntimeShader(LENS_AGSL)
                shader.setFloatUniform("size", w, h)
                shader.setFloatUniform("radius", cornerPx)
                val lens = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
                AndroidRenderEffect.createChainEffect(lens, blur)
            }.getOrNull()
        if (result != null) return result.asComposeRenderEffect()
    }
    return blur.asComposeRenderEffect()
}

/** Tandai area konten utama yang akan menjadi latar blur. Tanpa efek (nol overhead) bila kaca OFF. */
@Composable
fun Modifier.glassSource(): Modifier {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null || !rememberBlurActive()) return this
    return this
        .onGloballyPositioned { backdrop.origin = it.positionInWindow() }
        .drawWithContent {
            backdrop.layer.record(
                this,
                layoutDirection,
                IntSize(size.width.roundToInt(), size.height.roundToInt()),
            ) {
                this@drawWithContent.drawContent()
            }
            drawLayer(backdrop.layer)
        }
}

/** Gambar latar yang diburamkan + lensa di belakang komponen ini. Pasang setelah clip, sebelum background. */
@Composable
fun Modifier.glassBlur(radius: Dp = 26.dp, lensRadius: Dp = 32.dp): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val profile = rememberGlassBlurProfile()
    if (backdrop == null || !profile.enabled) return this
    val blurLayer = rememberGraphicsLayer()
    val lastSize = remember { intArrayOf(0, 0) }
    val lastProfile = remember { arrayOfNulls<GlassBlurProfile>(1) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawWithContent {
            val w = size.width.roundToInt()
            val h = size.height.roundToInt()
            if (lastSize[0] != w || lastSize[1] != h || lastProfile[0] != profile) {
                lastSize[0] = w
                lastSize[1] = h
                lastProfile[0] = profile
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val qualityRadius = radius.toPx() * profile.radiusScale
                    val qualityLensRadius = lensRadius.toPx() * profile.radiusScale
                    blurLayer.renderEffect =
                        buildGlassEffect(
                            qualityRadius,
                            w.toFloat(),
                            h.toFloat(),
                            qualityLensRadius,
                            profile.useLens,
                        )
                }
            }
            blurLayer.record(this, layoutDirection, IntSize(w, h)) {
                translate(backdrop.origin.x - origin.x, backdrop.origin.y - origin.y) {
                    drawLayer(backdrop.layer)
                }
            }
            drawLayer(blurLayer)
            // Highlight lembut di sisi atas, memberi kesan kedalaman kaca
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                        endY = size.height * 0.65f,
                    ),
            )
            drawContent()
        }
}

/** Garis tepi kaca: terang di sudut kiri atas, redup di tengah, terang tipis di kanan bawah. */
fun Modifier.glassBorder(shape: Shape, glass: Boolean): Modifier =
    this.border(
        width = 1.dp,
        brush =
            Brush.linearGradient(
                if (glass) {
                    listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.22f))
                } else {
                    listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.10f))
                },
            ),
        shape = shape,
    )

/**
 * Blur latar di belakang jendela dialog/bottom sheet (Android 12+). Dipakai karena sheet dan dialog
 * tampil di jendela terpisah sehingga tidak bisa memakai GraphicsLayer layar utama.
 * Aman: bila perangkat tidak mendukung blur lintas-jendela, tidak terjadi apa-apa.
 */
@Composable
fun GlassWindowBlur(radiusPx: Int = 48) {
    val context = LocalContext.current
    val view = LocalView.current
    val profile = rememberGlassBlurProfile()
    val active = profile.enabled
    SideEffect {
        if (!active) return@SideEffect
        runCatching {
            var v: View? = view
            var window: android.view.Window? = null
            while (v != null && window == null) {
                window = (v as? DialogWindowProvider)?.window
                v = v.parent as? View
            }
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && window != null && wm?.isCrossWindowBlurEnabled == true) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.setBackgroundBlurRadius((radiusPx * profile.radiusScale).toInt().coerceAtLeast(4))
            }
        }
    }
}
