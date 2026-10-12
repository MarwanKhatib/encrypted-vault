package com.example.encryptedvault.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Decoding helpers for plaintext source files: thumbnails, scan-size bitmaps and video frames. */
object Thumbnails {
    /** Longest edge of a stored grid thumbnail. */
    private const val THUMBNAIL_EDGE = 400

    /** Grid thumbnail, upright (EXIF rotation applied). */
    fun generate(
        file: File,
        mime: String,
    ): Bitmap =
        decodeUpright(file, mime, THUMBNAIL_EDGE)
            ?: Bitmap.createBitmap(THUMBNAIL_EDGE, THUMBNAIL_EDGE, Bitmap.Config.ARGB_8888)

    /** Decodes [file] to at most [maxEdge] px on the longest side, or null if it cannot be decoded. */
    fun decodeUpright(
        file: File,
        mime: String,
        maxEdge: Int,
    ): Bitmap? =
        runCatching {
            when {
                mime.startsWith("video/") -> videoFrames(file, 1, maxEdge).firstOrNull()
                mime == "image/heic" || mime == "image/heif" ->
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                        val scale = maxOf(info.size.width, info.size.height).toFloat() / maxEdge
                        if (scale > 1) decoder.setTargetSize((info.size.width / scale).toInt(), (info.size.height / scale).toInt())
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                else -> {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(file.path, it) }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
                    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                    bitmap?.let { rotate(it, runCatching { ExifInterface(file.path).rotationDegrees }.getOrDefault(0)) }
                }
            }
        }.getOrNull()

    /** Up to [count] frames spread evenly over a video, each scaled to [maxEdge]. */
    fun videoFrames(
        file: File,
        count: Int,
        maxEdge: Int,
    ): List<Bitmap> =
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.path)
                val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
                (0 until count).mapNotNull { i ->
                    val atUs = if (count == 1) 0L else durationUs * i / count
                    retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { scaleDown(it, maxEdge) }
                }
            }
        }.getOrDefault(emptyList())

    /** Pixel size `(width, height)` of an image or video, or `(0, 0)` if it cannot be read. */
    fun dimensions(
        file: File,
        mime: String,
    ): Pair<Int, Int> =
        runCatching {
            if (mime.startsWith("video/")) {
                MediaMetadataRetriever().use { r ->
                    r.setDataSource(file.path)
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    w to h
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(file.path, it) }
                bounds.outWidth.coerceAtLeast(0) to bounds.outHeight.coerceAtLeast(0)
            }
        }.getOrDefault(0 to 0)

    /** Duration of a video in millis, or 0 for non-video / unreadable files. */
    fun videoDurationMs(file: File): Long =
        runCatching {
            MediaMetadataRetriever().use { r ->
                r.setDataSource(file.path)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
        }.getOrDefault(0L)

    /** Applies an EXIF rotation (BitmapFactory ignores it; ImageDecoder already honours it). */
    fun rotate(
        bitmap: Bitmap,
        degrees: Int,
    ): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Scales [bitmap] so its longest edge is at most [maxEdge]; smaller bitmaps are returned unchanged. */
    private fun scaleDown(
        bitmap: Bitmap,
        maxEdge: Int,
    ): Bitmap {
        val scale = maxEdge.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale >= 1f) return bitmap
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }

    /** Gap 10: strip location (and optionally time) from a decrypted temp copy before sharing. */
    fun stripSensitiveExif(
        file: File,
        stripTime: Boolean = false,
    ) {
        val exif = ExifInterface(file.path)
        listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
        ).forEach { exif.setAttribute(it, null) }
        if (stripTime) exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null)
        exif.saveAttributes()
    }
}
