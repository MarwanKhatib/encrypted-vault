package com.example.encryptedvault.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.encryptedvault.media.CropBox
import com.example.encryptedvault.media.CropCorner
import kotlin.math.roundToInt

private val HANDLE_TOUCH = 48.dp // Material minimum touch target
private val HANDLE_DOT = 14.dp
private val FRAME_WIDTH = 2.dp
private val DIM = Color.Black.copy(alpha = 0.5f)
private val FRAME = Color.White

/**
 * Dims everything outside the crop frame and puts a draggable handle on each corner.
 * Drags are reported to [onCropChange] as image fractions, so the frame keeps its shape when [lockRatio] is set.
 */
@Composable
fun CropOverlay(
    crop: CropBox,
    lockRatio: Float?,
    imageAspect: Float,
    onCropChange: (CropBox) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val currentCrop by rememberUpdatedState(crop)
    val currentRatio by rememberUpdatedState(lockRatio)
    val currentAspect by rememberUpdatedState(imageAspect)
    val currentOnChange by rememberUpdatedState(onCropChange)

    Box(modifier.onSizeChanged { boxSize = it }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val l = crop.left * w
            val t = crop.top * h
            val r = crop.right * w
            val b = crop.bottom * h
            drawRect(DIM, Offset.Zero, Size(w, t))
            drawRect(DIM, Offset(0f, b), Size(w, h - b))
            drawRect(DIM, Offset(0f, t), Size(l, b - t))
            drawRect(DIM, Offset(r, t), Size(w - r, b - t))
            drawRect(FRAME, Offset(l, t), Size(r - l, b - t), style = Stroke(width = FRAME_WIDTH.toPx()))
        }
        CropCorner.entries.forEach { corner ->
            val x = if (corner.onLeft) crop.left else crop.right
            val y = if (corner.onTop) crop.top else crop.bottom
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (x * boxSize.width - HANDLE_TOUCH.toPx() / 2).roundToInt(),
                            (y * boxSize.height - HANDLE_TOUCH.toPx() / 2).roundToInt(),
                        )
                    }.size(HANDLE_TOUCH)
                    .semantics { contentDescription = "Crop corner ${corner.name.lowercase().replace('_', ' ')}" }
                    .pointerInput(corner) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            if (boxSize.width == 0 || boxSize.height == 0) return@detectDragGestures
                            currentOnChange(
                                currentCrop.dragCorner(
                                    corner,
                                    drag.x / boxSize.width,
                                    drag.y / boxSize.height,
                                    currentAspect,
                                    currentRatio,
                                ),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(HANDLE_DOT).background(FRAME, CircleShape))
            }
        }
    }
}
