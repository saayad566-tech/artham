package `in`.artham.fundamentals.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import `in`.artham.fundamentals.core.Fmt
import `in`.artham.fundamentals.domain.PePoint
import `in`.artham.fundamentals.domain.PriceBar
import `in`.artham.fundamentals.ui.theme.ArthamColors
import kotlin.math.abs

private const val PRICE_REGION = 0.76f

/**
 * Candlestick chart with optional moving-average overlays and a volume strip.
 *
 * Drawn with the platform Canvas rather than a charting dependency: the shapes needed here
 * are a rectangle and a line, and a library would add megabytes to the APK for that.
 */
@Composable
fun CandleChart(
    bars: List<PriceBar>,
    smaShort: List<Double?>? = null,
    smaLong: List<Double?>? = null,
    showVolume: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (bars.size < 2) {
        Text(
            "Not enough price history to draw a chart. Run a sync for this company.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val up = ArthamColors.Positive
    val down = ArthamColors.Negative
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val shortColor = ArthamColors.Sky
    val longColor = ArthamColors.Amber
    val volumeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    var min = Double.MAX_VALUE
    var max = -Double.MAX_VALUE
    for (bar in bars) {
        if (bar.low < min) min = bar.low
        if (bar.high > max) max = bar.high
    }
    smaShort?.forEach { if (it != null) { if (it < min) min = it; if (it > max) max = it } }
    smaLong?.forEach { if (it != null) { if (it < min) min = it; if (it > max) max = it } }
    if (!min.isFinite() || !max.isFinite() || max <= min) return
    val pad = (max - min) * 0.06
    min -= pad
    max += pad
    val span = max - min
    val maxVolume = bars.maxOfOrNull { it.volume }?.coerceAtLeast(1L) ?: 1L

    val stats = bars.let { list ->
        val first = list.first().close
        val last = list.last().close
        (first to last)
    }
    val rising = stats.second >= stats.first
    val lineColor = if (rising) up else down

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fmt.rupees(bars.last().close, 2),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                color = lineColor
            )
            Spacer(Modifier.width(8.dp))
            val change = if (stats.first > 0) (stats.second - stats.first) / stats.first * 100.0 else null
            if (change != null) {
                Text(
                    "${if (change >= 0) "+" else ""}${Fmt.pct(change)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = lineColor
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${Fmt.num(max, 0)} / ${Fmt.num(min, 0)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Canvas(Modifier.fillMaxWidth().height(if (showVolume) 260.dp else 200.dp)) {
            val w = size.width
            val h = size.height
            val priceHeight = h * PRICE_REGION
            val volumeTop = priceHeight + h * 0.04f
            val volumeHeight = h - volumeTop
            val slot = w / bars.size
            val bodyWidth = (slot * 0.62f).coerceAtLeast(1.2f)

            fun yFor(price: Double): Float = (priceHeight - ((price - min) / span) * priceHeight * 0.94f - priceHeight * 0.03f).toFloat()

            // horizontal grid
            for (i in 1..3) {
                val gy = priceHeight * i / 4f
                drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
            }
            drawLine(grid, Offset(0f, priceHeight), Offset(w, priceHeight), strokeWidth = 1.4f)

            // candles
            bars.forEachIndexed { index, bar ->
                val cx = index * slot + slot / 2f
                val bullish = bar.close >= bar.open
                val color = if (bullish) up else down
                drawLine(
                    color,
                    Offset(cx, yFor(bar.high)),
                    Offset(cx, yFor(bar.low)),
                    strokeWidth = 1.1f
                )
                val top = yFor(maxOf(bar.open, bar.close))
                val bottom = yFor(minOf(bar.open, bar.close))
                val bodyHeight = (bottom - top).coerceAtLeast(1.2f)
                drawRect(
                    color.copy(alpha = if (bullish) 0.9f else 0.85f),
                    topLeft = Offset(cx - bodyWidth / 2f, top),
                    size = Size(bodyWidth, bodyHeight)
                )
                if (showVolume && bar.volume > 0) {
                    val vh = (bar.volume.toFloat() / maxVolume) * volumeHeight
                    drawRect(
                        volumeColor,
                        topLeft = Offset(cx - bodyWidth / 2f, volumeTop + volumeHeight - vh),
                        size = Size(bodyWidth, vh)
                    )
                }
            }

            fun drawOverlay(series: List<Double?>?, color: Color) {
                if (series == null) return
                val path = Path()
                var started = false
                series.forEachIndexed { index, value ->
                    if (value == null) return@forEachIndexed
                    val cx = index * slot + slot / 2f
                    val cy = yFor(value)
                    if (!started) {
                        path.moveTo(cx, cy); started = true
                    } else {
                        path.lineTo(cx, cy)
                    }
                }
                if (started) drawPath(path, color, style = Stroke(width = 1.6f))
            }
            drawOverlay(smaShort, shortColor)
            drawOverlay(smaLong, longColor)
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                bars.first().let { java.text.SimpleDateFormat("MMM yy", java.util.Locale.US).format(java.util.Date(it.time)) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (smaShort != null) {
                Text("50 DMA", style = MaterialTheme.typography.labelSmall, color = ArthamColors.Sky)
                Spacer(Modifier.width(10.dp))
            }
            if (smaLong != null) {
                Text("200 DMA", style = MaterialTheme.typography.labelSmall, color = ArthamColors.Amber)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                bars.last().let { java.text.SimpleDateFormat("dd MMM yy", java.util.Locale.US).format(java.util.Date(it.time)) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Price divided by trailing reported EPS, with the median and the 20th/80th percentile
 * drawn as reference lines. Reading a multiple against its own history is the cheapest
 * defence against paying a peak price for a peak multiple.
 */
@Composable
fun PeBandChart(band: List<PePoint>, modifier: Modifier = Modifier) {
    if (band.size < 20) {
        Text(
            "A price to earnings band needs at least twenty observations and four years of reported earnings.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val values = band.map { it.pe }.sorted()
    val p20 = values[(values.size * 0.2).toInt().coerceIn(0, values.size - 1)]
    val median = values[values.size / 2]
    val p80 = values[(values.size * 0.8).toInt().coerceIn(0, values.size - 1)]
    val current = band.last().pe

    var min = values.first()
    var max = values.last()
    if (max <= min) max = min + 1.0
    val pad = (max - min) * 0.08
    min -= pad
    max += pad
    val span = max - min

    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = ArthamColors.Sky
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(
                "P/E ${Fmt.times(current)}",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = lineColor
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "median ${Fmt.times(median)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                "20th ${Fmt.times(p20)}  •  80th ${Fmt.times(p80)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = bandColor
            )
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val w = size.width
            val h = size.height
            fun yFor(pe: Double): Float = (h - ((pe - min) / span) * h * 0.92f - h * 0.04f).toFloat()

            for (level in listOf(p20, median, p80)) {
                drawLine(grid, Offset(0f, yFor(level)), Offset(w, yFor(level)), strokeWidth = 1f, pathEffect = dash)
            }
            val step = w / (band.size - 1).coerceAtLeast(1)
            val path = Path()
            band.forEachIndexed { index, point ->
                val x = index * step
                val y = yFor(point.pe)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 1.8f))
            drawCircle(lineColor, radius = 3f, center = Offset(w, yFor(current)))
        }
    }
}

/** A horizontal shareholding bar, used for the ownership tab. */
@Composable
fun StackedBar(segments: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val total = segments.sumOf { it.second }.takeIf { it > 0 } ?: return
    val colors = listOf(
        ArthamColors.Mint, ArthamColors.Sky, ArthamColors.Amber,
        ArthamColors.Rose, ArthamColors.TextTertiary
    )
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            var x = 0f
            segments.forEachIndexed { index, (_, value) ->
                val segmentWidth = (value / total).toFloat() * size.width
                drawRect(
                    colors[index % colors.size].copy(alpha = 0.85f),
                    topLeft = Offset(x, 0f),
                    size = Size(segmentWidth.coerceAtLeast(0f), size.height)
                )
                x += segmentWidth
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            segments.forEachIndexed { index, (label, value) ->
                if (abs(value) < 0.01) return@forEachIndexed
                Text(
                    "● $label ${Fmt.pct(value)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors[index % colors.size],
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}
