package com.carecircle.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A rectangle in pixels. A tiny type of our own so this file stays free of android.* classes. */
data class FRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Where the framing guide on screen lands in the captured picture, so only the printed strip is read. */
object CropMath {
    const val MAX_SIDE = 2048
    /** Extra border around the guide, as a fraction of its size, so a letter on the edge is not cut. */
    const val MARGIN = 0.08f

    /** The guide as drawn on the preview: fractions of the view. */
    val GUIDE_WIDTH = 0.86f
    val GUIDE_HEIGHT = 0.20f
    val GUIDE_TOP = 0.34f

    fun guideInView(viewW: Float, viewH: Float): FRect {
        val w = viewW * GUIDE_WIDTH; val h = viewH * GUIDE_HEIGHT
        val left = (viewW - w) / 2f; val top = viewH * GUIDE_TOP
        return FRect(left, top, left + w, top + h)
    }

    /** The picture's size once it is turned upright: a sideways sensor image (90 or 270) swaps width and height. */
    fun uprightSize(imageW: Int, imageH: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (rotationDegrees % 180 == 0) imageW to imageH else imageH to imageW

    /**
     * Maps [guide] (view pixels) into the upright picture, for a preview that scales the picture to fill the view and centres it
     * (FILL_CENTER), cutting what overflows. Adds [MARGIN], clamps to the picture, and returns null when nothing usable is left.
     */
    fun guideToImage(guide: FRect, viewW: Float, viewH: Float, imageW: Int, imageH: Int, rotationDegrees: Int, margin: Float = MARGIN): FRect? {
        if (viewW <= 0f || viewH <= 0f || imageW <= 0 || imageH <= 0) return null
        val (uw, uh) = uprightSize(imageW, imageH, rotationDegrees)
        val scale = max(viewW / uw, viewH / uh)
        val offX = (uw * scale - viewW) / 2f
        val offY = (uh * scale - viewH) / 2f
        val padX = guide.width * margin; val padY = guide.height * margin
        val l = ((guide.left - padX + offX) / scale).coerceIn(0f, uw.toFloat())
        val t = ((guide.top - padY + offY) / scale).coerceIn(0f, uh.toFloat())
        val r = ((guide.right + padX + offX) / scale).coerceIn(0f, uw.toFloat())
        val b = ((guide.bottom + padY + offY) / scale).coerceIn(0f, uh.toFloat())
        return if (r - l >= 8f && b - t >= 8f) FRect(l, t, r, b) else null
    }

    /** The size to shrink to so the longest side is at most [maxSide]. Never enlarges. */
    fun scaleToMax(w: Int, h: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        val longest = max(w, h)
        if (longest <= maxSide || longest == 0) return w to h
        val f = maxSide.toFloat() / longest
        return max(1, (w * f).roundToInt()) to max(1, (h * f).roundToInt())
    }

    /** Integer rectangle for Bitmap.createBitmap, kept inside a picture of [w] by [h]. */
    fun toPixels(r: FRect, w: Int, h: Int): IntArray {
        val x = r.left.roundToInt().coerceIn(0, w - 1)
        val y = r.top.roundToInt().coerceIn(0, h - 1)
        return intArrayOf(x, y, min(w - x, max(1, r.width.roundToInt())), min(h - y, max(1, r.height.roundToInt())))
    }
}
