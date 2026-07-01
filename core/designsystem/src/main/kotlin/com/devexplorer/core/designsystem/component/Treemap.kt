package com.devexplorer.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.devexplorer.core.designsystem.util.formatBytes

/** One rectangle in the treemap: a label, its weight, and its colors. */
data class TreemapSlice(
    val label: String,
    val value: Long,
    val color: Color,
    val onColor: Color,
)

/**
 * A treemap: nested rectangles whose areas are proportional to [TreemapSlice.value].
 *
 * Layout uses a recursive split that always divides along the longer edge, which
 * keeps rectangles reasonably square (a simple cousin of the "squarified"
 * algorithm). Everything is drawn on a single [Canvas], and labels are rendered
 * only where a rectangle is big enough to fit them — so it reads cleanly at any
 * size. Slices should be pre-sorted largest-first for the nicest layout.
 */
@Composable
fun Treemap(
    slices: List<TreemapSlice>,
    modifier: Modifier = Modifier,
) {
    if (slices.isEmpty()) return
    val measurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val placed = squarify(slices, Rect(0f, 0f, size.width, size.height))
        placed.forEach { (slice, rect) ->
            drawRect(
                color = slice.color,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
            )
            // A hairline gap so adjacent tiles read as separate.
            drawRect(
                color = Color.Black.copy(alpha = 0.12f),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                style = Stroke(width = 2f),
            )
            if (rect.width > 92f && rect.height > 44f) {
                val layout = measurer.measure(
                    text = AnnotatedString("${slice.label}\n${formatBytes(slice.value)}"),
                    style = TextStyle(color = slice.onColor, fontSize = 12.sp),
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(rect.left + 10f, rect.top + 8f),
                )
            }
        }
    }
}

private fun squarify(slices: List<TreemapSlice>, area: Rect): List<Pair<TreemapSlice, Rect>> {
    if (slices.isEmpty()) return emptyList()
    if (slices.size == 1) return listOf(slices[0] to area)

    val total = slices.sumOf { it.value }.toFloat()
    // Find a split point where the first group is ~half the total value.
    var acc = 0f
    var i = 0
    while (i < slices.size - 1 && acc + slices[i].value < total / 2f) {
        acc += slices[i].value
        i++
    }
    val splitIndex = (i + 1).coerceIn(1, slices.size - 1)
    val groupA = slices.subList(0, splitIndex)
    val groupB = slices.subList(splitIndex, slices.size)
    val fraction = if (total > 0f) groupA.sumOf { it.value }.toFloat() / total else 0.5f

    return if (area.width >= area.height) {
        val split = area.left + area.width * fraction
        squarify(groupA, Rect(area.left, area.top, split, area.bottom)) +
            squarify(groupB, Rect(split, area.top, area.right, area.bottom))
    } else {
        val split = area.top + area.height * fraction
        squarify(groupA, Rect(area.left, area.top, area.right, split)) +
            squarify(groupB, Rect(area.left, split, area.right, area.bottom))
    }
}
