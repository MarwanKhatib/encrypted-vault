package com.example.encryptedvault.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint

/** Crop shapes offered by the editor; ratio is width / height of the result (null = keep as is). */
enum class CropAspect(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null), SQUARE("1:1", 1f), FOUR_THREE("4:3", 4f / 3f), THREE_FOUR("3:4", 3f / 4f), SIXTEEN_NINE("16:9", 16f / 9f), NINE_SIXTEEN("9:16", 9f / 16f)
}

/** One-tap looks, applied before the manual sliders. */
enum class PhotoFilter(val label: String) { NONE("Original"), VIVID("Vivid"), WARM("Warm"), COOL("Cool"), MONO("Mono"), FADE("Fade") }

/** Non-destructive description of an edit; the original vaulted file is never touched (the result is a new item). */
data class Edit(
    val rotation: Int = 0, // clockwise degrees, multiple of 90
    val flipHorizontal: Boolean = false,
    val aspect: CropAspect = CropAspect.ORIGINAL,
    val filter: PhotoFilter = PhotoFilter.NONE,
    val brightness: Float = 0f, // -1..1
    val contrast: Float = 1f, // 0.5..1.5
    val saturation: Float = 1f, // 0..2
) {
    val isIdentity get() = this == Edit()
    /** Adds [by] degrees clockwise, normalised to 0..359. */
    fun rotated(by: Int) = copy(rotation = ((rotation + by) % 360 + 360) % 360)
}

/** Applies an [Edit] to a bitmap. The crop and colour-matrix math is pure so it is unit-tested; only [apply] needs Android. */
object ImageEdits {
    /** Largest centred rectangle of [ratio] (w/h) inside w×h, as left, top, width, height. */
    fun cropRect(w: Int, h: Int, ratio: Float?): IntArray {
        if (ratio == null || w <= 0 || h <= 0) return intArrayOf(0, 0, w, h)
        val cropW = minOf(w.toFloat(), h * ratio).toInt().coerceAtLeast(1)
        val cropH = minOf(h.toFloat(), w / ratio).toInt().coerceAtLeast(1)
        return intArrayOf((w - cropW) / 2, (h - cropH) / 2, cropW, cropH)
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
        val (l, t, w, h) = cropRect(oriented.width, oriented.height, e.aspect.ratio)
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
