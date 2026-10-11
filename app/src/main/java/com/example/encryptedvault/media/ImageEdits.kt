package com.example.encryptedvault.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.ceil
import kotlin.math.floor

/** Crop shapes offered by the editor; ratio is width / height of the result (null = unlocked, drag freely). */
enum class CropAspect(val label: String, val ratio: Float?) {
    FREE("Free", null), SQUARE("1:1", 1f), FOUR_THREE("4:3", 4f / 3f), THREE_FOUR("3:4", 3f / 4f), SIXTEEN_NINE("16:9", 16f / 9f), NINE_SIXTEEN("9:16", 9f / 16f)
}

/** Corner of the crop frame that the user drags. */
enum class CropCorner(val onLeft: Boolean, val onTop: Boolean) {
    TOP_LEFT(onLeft = true, onTop = true), TOP_RIGHT(onLeft = false, onTop = true),
    BOTTOM_LEFT(onLeft = true, onTop = false), BOTTOM_RIGHT(onLeft = false, onTop = false),
}

/** One-tap looks, applied before the manual sliders. */
enum class PhotoFilter(val label: String) { NONE("Original"), VIVID("Vivid"), WARM("Warm"), COOL("Cool"), MONO("Mono"), FADE("Fade") }

/**
 * Crop frame as fractions (0..1) of the oriented image, so it is the same at preview and full resolution.
 * (0, 0, 1, 1) keeps the whole picture.
 */
data class CropBox(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val isFull get() = this == CropBox()

    /**
     * Moves [corner] by ([dx], [dy]) image fractions while the opposite corner stays put.
     * [imageAspect] is the oriented image's width / height. A non-null [ratio] (output width / height) keeps the frame at that shape.
     */
    fun dragCorner(corner: CropCorner, dx: Float, dy: Float, imageAspect: Float, ratio: Float?): CropBox {
        val fixedX = if (corner.onLeft) right else left
        val fixedY = if (corner.onTop) bottom else top
        val dirX = if (corner.onLeft) -1f else 1f
        val dirY = if (corner.onTop) -1f else 1f
        val maxW = if (dirX < 0) fixedX else 1f - fixedX
        val maxH = if (dirY < 0) fixedY else 1f - fixedY
        val movedX = (if (corner.onLeft) left else right) + dx
        val movedY = (if (corner.onTop) top else bottom) + dy
        var w = clampSide(dirX * (movedX.coerceIn(0f, 1f) - fixedX), maxW)
        var h = clampSide(dirY * (movedY.coerceIn(0f, 1f) - fixedY), maxH)
        if (ratio != null) {
            val fractionRatio = ratio / imageAspect // width / height in image fractions
            h = clampSide(w / fractionRatio, maxH)
            w = clampSide(h * fractionRatio, maxW)
            h = w / fractionRatio
        }
        val x = fixedX + dirX * w
        val y = fixedY + dirY * h
        return CropBox(minOf(fixedX, x), minOf(fixedY, y), maxOf(fixedX, x), maxOf(fixedY, y))
    }

    /** Crop as left, top, width, height in pixels of a [width] × [height] image. Always at least 1 px. */
    fun pixelRect(width: Int, height: Int): IntArray {
        val l = floor(left * width).toInt().coerceIn(0, width - 1)
        val t = floor(top * height).toInt().coerceIn(0, height - 1)
        val r = ceil(right * width).toInt().coerceIn(l + 1, width)
        val b = ceil(bottom * height).toInt().coerceIn(t + 1, height)
        return intArrayOf(l, t, r - l, b - t)
    }

    private fun clampSide(value: Float, max: Float) = value.coerceAtLeast(MIN_SIDE).coerceAtMost(max)

    private companion object {
        /** Smallest side as a fraction of the image, so the frame cannot collapse to nothing. */
        const val MIN_SIDE = 0.05f
    }
}

/**
 * Non-destructive description of an edit; the original vaulted file is never touched (the result is a new item).
 * Rotating and flipping reset [crop], because the crop is in oriented-image coordinates.
 */
data class Edit(
    val rotation: Int = 0, // clockwise degrees, multiple of 90
    val flipHorizontal: Boolean = false,
    val aspect: CropAspect = CropAspect.FREE, // locks the crop frame while dragging
    val crop: CropBox = CropBox(),
    val filter: PhotoFilter = PhotoFilter.NONE,
    val brightness: Float = 0f, // -1..1
    val contrast: Float = 1f, // 0.5..1.5
    val saturation: Float = 1f, // 0..2
) {
    val isIdentity get() = this == Edit()

    /** Adds [by] degrees clockwise, normalised to 0..359. */
    fun rotated(by: Int) = copy(rotation = ((rotation + by) % 360 + 360) % 360, crop = CropBox())

    fun flipped() = copy(flipHorizontal = !flipHorizontal, crop = CropBox())

    /** Selects [preset]; a ratio preset centres the largest frame of that shape on an image of [imageAspect]. */
    fun withAspect(preset: CropAspect, imageAspect: Float) =
        copy(aspect = preset, crop = preset.ratio?.let { ImageEdits.presetCrop(it, imageAspect) } ?: crop)
}

/** Applies an [Edit] to a bitmap. The crop and colour-matrix math is pure so it is unit-tested; only [apply] needs Android. */
object ImageEdits {
    /** Largest centred frame of [ratio] (w/h) on an image of [imageAspect] (w/h), as fractions. */
    fun presetCrop(ratio: Float, imageAspect: Float): CropBox =
        if (ratio >= imageAspect) {
            val h = imageAspect / ratio
            CropBox(0f, (1f - h) / 2f, 1f, (1f + h) / 2f)
        } else {
            val w = ratio / imageAspect
            CropBox((1f - w) / 2f, 0f, (1f + w) / 2f, 1f)
        }

    /** Width / height of the image after rotating it by [rotation] degrees (a multiple of 90). */
    fun orientedAspect(width: Int, height: Int, rotation: Int): Float {
        val aspect = width.toFloat() / height
        return if (rotation % 180 == 0) aspect else 1f / aspect
    }

    /** 4×5 colour matrix (row-major, 20 floats) for the filter plus sliders. */
    fun colorMatrix(e: Edit): FloatArray {
        val m = ColorMatrix()
        when (e.filter) {
            PhotoFilter.NONE -> Unit
            PhotoFilter.VIVID -> m.postConcat(saturate(1.4f).also { it.postConcat(contrast(1.1f)) })
            PhotoFilter.WARM -> m.postConcat(ColorMatrix(floatArrayOf(1.08f, 0f, 0f, 0f, 6f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0.9f, 0f, -6f, 0f, 0f, 0f, 1f, 0f)))
            PhotoFilter.COOL -> m.postConcat(ColorMatrix(floatArrayOf(0.92f, 0f, 0f, 0f, -6f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1.08f, 0f, 6f, 0f, 0f, 0f, 1f, 0f)))
            PhotoFilter.MONO -> m.postConcat(saturate(0f))
            PhotoFilter.FADE -> m.postConcat(contrast(0.85f).also { it.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, 20f, 0f, 1f, 0f, 0f, 20f, 0f, 0f, 1f, 0f, 20f, 0f, 0f, 0f, 1f, 0f))) })
        }
        m.postConcat(saturate(e.saturation))
        m.postConcat(contrast(e.contrast))
        m.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, e.brightness * 255f, 0f, 1f, 0f, 0f, e.brightness * 255f, 0f, 0f, 1f, 0f, e.brightness * 255f, 0f, 0f, 0f, 1f, 0f)))
        return m.array
    }

    /** Colour matrix with the given saturation (0 grey, 1 unchanged). */
    private fun saturate(s: Float) = ColorMatrix().also { it.setSaturation(s) }

    /** Colour matrix scaling contrast around mid-grey (1 unchanged). */
    private fun contrast(c: Float): ColorMatrix {
        val t = (1f - c) * 128f
        return ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
    }

    /** Returns a new bitmap with [e] applied: orientation first, then crop, then colour. */
    fun apply(src: Bitmap, e: Edit): Bitmap {
        if (e.isIdentity) return src
        val geometry = Matrix().apply {
            if (e.flipHorizontal) postScale(-1f, 1f, src.width / 2f, src.height / 2f)
            if (e.rotation != 0) postRotate(e.rotation.toFloat(), src.width / 2f, src.height / 2f)
        }
        val oriented = Bitmap.createBitmap(src, 0, 0, src.width, src.height, geometry, true)
        val (l, t, w, h) = e.crop.pixelRect(oriented.width, oriented.height)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(colorMatrix(e)) }
        Canvas(out).drawBitmap(oriented, -l.toFloat(), -t.toFloat(), paint)
        if (oriented !== src) oriented.recycle()
        return out
    }

    /** "IMG_1.png" -> "IMG_1 (edited).jpg"; edits are always saved as JPEG. */
    fun editedName(name: String): String {
        val base = name.substringBeforeLast('.', name).trim().ifEmpty { "Photo" }
        return "$base (edited).jpg"
    }
}
