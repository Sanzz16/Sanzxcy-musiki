package com.metrolist.music.community

import kotlin.math.max

/** Pixel-space placement and source-image bounds for a fully covered crop frame. */
internal data class ProfileCropGeometry(
    val scale: Float,
    val imageLeft: Float,
    val imageTop: Float,
    val imageWidth: Float,
    val imageHeight: Float,
    val panX: Float,
    val panY: Float,
    val sourceLeft: Float,
    val sourceTop: Float,
    val sourceRight: Float,
    val sourceBottom: Float,
)

/**
 * Returns a center-cover placement with a clamped translation. Zoom 1 is the smallest
 * image that covers the frame; zoom is capped to avoid an unusable, extremely small crop.
 */
internal fun calculateProfileCropGeometry(
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Float,
    viewportHeight: Float,
    zoom: Float,
    panX: Float,
    panY: Float,
): ProfileCropGeometry {
    require(imageWidth > 0 && imageHeight > 0)
    require(viewportWidth > 0f && viewportHeight > 0f)

    val baseScale = max(viewportWidth / imageWidth, viewportHeight / imageHeight)
    val scale = baseScale * (if (zoom.isFinite()) zoom else 1f).coerceIn(1f, 8f)
    val drawnWidth = imageWidth * scale
    val drawnHeight = imageHeight * scale
    val maxPanX = ((drawnWidth - viewportWidth) / 2f).coerceAtLeast(0f)
    val maxPanY = ((drawnHeight - viewportHeight) / 2f).coerceAtLeast(0f)
    val boundedPanX = (if (panX.isFinite()) panX else 0f).coerceIn(-maxPanX, maxPanX)
    val boundedPanY = (if (panY.isFinite()) panY else 0f).coerceIn(-maxPanY, maxPanY)
    val left = (viewportWidth - drawnWidth) / 2f + boundedPanX
    val top = (viewportHeight - drawnHeight) / 2f + boundedPanY

    return ProfileCropGeometry(
        scale = scale,
        imageLeft = left,
        imageTop = top,
        imageWidth = drawnWidth,
        imageHeight = drawnHeight,
        panX = boundedPanX,
        panY = boundedPanY,
        sourceLeft = ((0f - left) / scale).coerceIn(0f, imageWidth.toFloat()),
        sourceTop = ((0f - top) / scale).coerceIn(0f, imageHeight.toFloat()),
        sourceRight = ((viewportWidth - left) / scale).coerceIn(0f, imageWidth.toFloat()),
        sourceBottom = ((viewportHeight - top) / scale).coerceIn(0f, imageHeight.toFloat()),
    )
}
