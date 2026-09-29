package com.example.ultraenhance.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun BeforeAfterSlider(
    original: Bitmap,
    enhanced: Bitmap,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember { mutableFloatStateOf(0.5f) }

    val originalImageBitmap = remember(original) { original.asImageBitmap() }
    val enhancedImageBitmap = remember(enhanced) { enhanced.asImageBitmap() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val newPosition = change.position.x / size.width
                    sliderPosition = newPosition.coerceIn(0f, 1f)
                }
            }
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val splitX = width * sliderPosition

            // Draw full enhanced image (right side / background)
            drawImage(
                image = enhancedImageBitmap,
                dstSize = IntSize(width.toInt(), height.toInt())
            )

            // Clip left side to draw original image
            val leftClipPath = Path().apply {
                addRect(Rect(0f, 0f, splitX, height))
            }

            clipPath(leftClipPath) {
                drawImage(
                    image = originalImageBitmap,
                    dstSize = IntSize(width.toInt(), height.toInt())
                )
            }

            // Draw divider line
            drawLine(
                color = Color.White,
                start = Offset(splitX, 0f),
                end = Offset(splitX, height),
                strokeWidth = 4.dp.toPx()
            )

            // Draw slider handle circle
            drawCircle(
                color = Color.White,
                radius = 16.dp.toPx(),
                center = Offset(splitX, height / 2f)
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.3f),
                radius = 12.dp.toPx(),
                center = Offset(splitX, height / 2f)
            )
        }

        // Labels overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "ORIGINAL",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "ENHANCED",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
