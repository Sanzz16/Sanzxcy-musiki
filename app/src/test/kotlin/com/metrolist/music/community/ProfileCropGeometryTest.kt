package com.metrolist.music.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCropGeometryTest {
    @Test
    fun `center cover maps frame to the matching source region`() {
        val geometry = calculateProfileCropGeometry(
            imageWidth = 1600,
            imageHeight = 900,
            viewportWidth = 400f,
            viewportHeight = 300f,
            zoom = 1f,
            panX = 0f,
            panY = 0f,
        )

        assertEquals(200f, geometry.sourceLeft, 0.01f)
        assertEquals(0f, geometry.sourceTop, 0.01f)
        assertEquals(1400f, geometry.sourceRight, 0.01f)
        assertEquals(900f, geometry.sourceBottom, 0.01f)
    }

    @Test
    fun `large pan is clamped at the source edge without empty frame regions`() {
        val leftEdge = calculateProfileCropGeometry(1600, 900, 400f, 300f, 1f, -10_000f, 0f)
        val rightEdge = calculateProfileCropGeometry(1600, 900, 400f, 300f, 1f, 10_000f, 0f)

        assertEquals(400f, leftEdge.sourceLeft, 0.01f)
        assertEquals(1600f, leftEdge.sourceRight, 0.01f)
        assertEquals(0f, rightEdge.sourceLeft, 0.01f)
        assertEquals(1200f, rightEdge.sourceRight, 0.01f)
        assertTrue(leftEdge.sourceTop >= 0f && leftEdge.sourceBottom <= 900f)
        assertTrue(rightEdge.sourceTop >= 0f && rightEdge.sourceBottom <= 900f)
    }

    @Test
    fun `zoom is bounded while crop remains inside source`() {
        val zoomedOut = calculateProfileCropGeometry(1200, 1800, 400f, 300f, 0.1f, 0f, 0f)
        val zoomedIn = calculateProfileCropGeometry(1200, 1800, 400f, 300f, 100f, 0f, 0f)

        assertEquals(1f, zoomedOut.scale / maxOf(400f / 1200f, 300f / 1800f), 0.0001f)
        assertEquals(8f, zoomedIn.scale / maxOf(400f / 1200f, 300f / 1800f), 0.0001f)
        listOf(zoomedOut, zoomedIn).forEach { geometry ->
            assertTrue(geometry.sourceLeft >= 0f)
            assertTrue(geometry.sourceTop >= 0f)
            assertTrue(geometry.sourceRight <= 1200f)
            assertTrue(geometry.sourceBottom <= 1800f)
            assertTrue(geometry.sourceRight > geometry.sourceLeft)
            assertTrue(geometry.sourceBottom > geometry.sourceTop)
        }
    }

    @Test
    fun `non-finite gesture values fall back to a centered crop`() {
        val expected = calculateProfileCropGeometry(1600, 900, 400f, 300f, 1f, 0f, 0f)
        val actual = calculateProfileCropGeometry(
            1600, 900, 400f, 300f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
        )

        assertEquals(expected, actual)
    }
}
