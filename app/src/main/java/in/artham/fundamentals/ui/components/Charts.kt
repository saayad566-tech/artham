package `in`.artham.fundamentals.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * A line chart of one series. Missing periods break the line rather than being
 * drawn as zero, so a gap in the data never looks like a collapse.
 */
@Composable
fun Sparkline(
    values: List<Double?>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    showArea: Boolean = true
) {
    val present = values.filterNotNull()
    if (present.size < 2) {
        Text(
            "Not enough periods to chart",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val min = present.min()
    val max = present.max()
    val span = (max - min).takeIf { abs(it) > 1e-9 } ?: 1.0
    Canvas(modifier.fillMaxWidth().height(72.dp)) {
        val w = size.width
        val h = size.height
        val stepX = if (values.size > 1) w / (values.size - 1) else w
        fun y(v: Double) = (h - ((v - min) / span) * (h * 0.86f) - h * 0.07f).toFloat()
        var previous: Offset? = null
        val line = Path()
        var started = false
        values.forEachIndexed { i, raw ->
            if (raw == null) {
                previous = null
                return@forEachIndexed
            }
            val point = Offset(i * stepX, y(raw))
            if (!started) {
                line.moveTo(point.x, point.y)
                started = true
            } else {
                line.lineTo(point.x, point.y)
            }
            previous = point
        }
        if (showArea && started) {
            val area = Path().apply {
                addPath(line)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(area, color.copy(alpha = 0.10f))
        }
        drawPath(line, color, style = Stroke(width = 2.2f))
        values.forEachIndexed { i, raw ->
            if (raw == null) return@forEachIndexed
            drawCircle(color, radius = 2.6f, center = Offset(i * stepX, y(raw)))
        }
    }
}

/** Vertical bars with labels underneath, used for quarterly and yearly comparisons. */
@Composable
fun BarSeries(
    labels: List<String>,
    values: List<Double?>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    if (values.isEmpty()) return
    val present = values.filterNotNull()
    if (present.isEmpty()) {
        Text(
            "No values to chart",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val max = present.maxOf { abs(it) }.takeIf { it > 1e-9 } ?: 1.0
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val count = values.size
            val slot = size.width / count
            val barWidth = slot * 0.56f
            values.forEachIndexed { i, raw ->
                val x = i * slot + (slot - barWidth) / 2f
                if (raw == null) {
                    drawRect(
                        Color.Gray.copy(alpha = 0.18f),
                        topLeft = Offset(x, size.height * 0.82f),
                        size = androidx.compose.ui.geometry.Size(barWidth, size.height * 0.02f)
                    )
                } else {
                    val fraction = (abs(raw) / max).toFloat()
                    val barHeight = (size.height * 0.82f) * fraction
                    drawRect(
                        if (raw >= 0) color else MaterialTheme.colorScheme.error,
                        topLeft = Offset(x, size.height * 0.82f - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                    )
                }
            }
            drawLine(
                Color.Gray.copy(alpha = 0.35f),
                Offset(0f, size.height * 0.82f),
                Offset(size.width, size.height * 0.82f),
                strokeWidth = 1f
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f).padding(horizontal = 1.dp)
                )
            }
        }
    }
}
