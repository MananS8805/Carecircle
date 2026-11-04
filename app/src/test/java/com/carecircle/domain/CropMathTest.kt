package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMathTest {
    private val eps = 0.5f
    private fun near(a: Float, b: Float) = assertEquals(a, b, eps)

    @Test fun guideIsCentredAndInTheUpperMiddle() {
        val g = CropMath.guideInView(1000f, 2000f)
        near(70f, g.left); near(930f, g.right); near(680f, g.top); near(1080f, g.bottom)
    }

    @Test fun sameAspectMapsByPlainScaling() {
        // A 1000x2000 view showing a 2000x4000 picture: everything doubles, no offset.
        val guide = FRect(100f, 400f, 900f, 800f)
        val r = CropMath.guideToImage(guide, 1000f, 2000f, 2000, 4000, 0, margin = 0f)!!
        near(200f, r.left); near(800f, r.top); near(1800f, r.right); near(1600f, r.bottom)
    }

    @Test fun aWiderPictureLosesItsSidesUnderFillCenter() {
        // 1000x1000 view, 2000x1000 picture: scale 1, so 500 px on each side are cut off.
        val guide = FRect(100f, 300f, 900f, 500f)
        val r = CropMath.guideToImage(guide, 1000f, 1000f, 2000, 1000, 0, margin = 0f)!!
        near(600f, r.left); near(1400f, r.right); near(300f, r.top); near(500f, r.bottom)
    }

    @Test fun aTallerPictureLosesTopAndBottom() {
        // 1000x1000 view, 1000x2000 picture: scale 1, 500 px cut from top and bottom.
        val guide = FRect(100f, 300f, 900f, 500f)
        val r = CropMath.guideToImage(guide, 1000f, 1000f, 1000, 2000, 0, margin = 0f)!!
        near(100f, r.left); near(900f, r.right); near(800f, r.top); near(1000f, r.bottom)
    }

    @Test fun aSidewaysSensorImageIsTurnedUprightFirst() {
        // 2000x1000 sensor image that must be rotated 90 degrees: upright it is 1000x2000.
        assertEquals(1000 to 2000, CropMath.uprightSize(2000, 1000, 90))
        assertEquals(1000 to 2000, CropMath.uprightSize(2000, 1000, 270))
        assertEquals(2000 to 1000, CropMath.uprightSize(2000, 1000, 180))
        val guide = FRect(100f, 300f, 900f, 500f)
        val rotated = CropMath.guideToImage(guide, 1000f, 1000f, 2000, 1000, 90, margin = 0f)!!
        val upright = CropMath.guideToImage(guide, 1000f, 1000f, 1000, 2000, 0, margin = 0f)!!
        assertEquals(upright, rotated)
    }

    @Test fun theMarginWidensTheCropButStaysInsideThePicture() {
        val guide = FRect(0f, 0f, 1000f, 1000f)
        val r = CropMath.guideToImage(guide, 1000f, 1000f, 1000, 1000, 0)!!
        near(0f, r.left); near(0f, r.top); near(1000f, r.right); near(1000f, r.bottom)
        val inner = CropMath.guideToImage(FRect(400f, 400f, 600f, 600f), 1000f, 1000f, 1000, 1000, 0)!!
        assertTrue(inner.width > 200f && inner.height > 200f)
    }

    @Test fun nothingUsableGivesNull() {
        assertNull(CropMath.guideToImage(FRect(0f, 0f, 10f, 10f), 0f, 100f, 100, 100, 0))
        assertNull(CropMath.guideToImage(FRect(0f, 0f, 10f, 10f), 100f, 100f, 0, 100, 0))
        // A guide entirely outside the picture collapses to nothing.
        assertNull(CropMath.guideToImage(FRect(5000f, 5000f, 5100f, 5100f), 100f, 100f, 100, 100, 0))
        assertNotNull(CropMath.guideToImage(FRect(10f, 10f, 90f, 90f), 100f, 100f, 100, 100, 0))
    }

    @Test fun downscaleKeepsTheAspectAndNeverEnlarges() {
        assertEquals(2048 to 1536, CropMath.scaleToMax(4096, 3072))
        assertEquals(1536 to 2048, CropMath.scaleToMax(3072, 4096))
        assertEquals(1000 to 800, CropMath.scaleToMax(1000, 800))
        assertEquals(2048 to 2048, CropMath.scaleToMax(2048, 2048))
    }

    @Test fun pixelRectangleStaysInsideTheBitmap() {
        val p = CropMath.toPixels(FRect(-5f, 10f, 5000f, 5000f), 100, 200)
        assertEquals(listOf(0, 10, 100, 190), p.toList())
    }
}
