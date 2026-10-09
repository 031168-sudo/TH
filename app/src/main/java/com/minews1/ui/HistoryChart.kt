package com.minews1.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minews1.HistoryDb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val PlotLeftPad = 4.dp
private val PlotRightPad = 44.dp
private val PlotTopPad = 4.dp
private val PlotBottomPad = 4.dp

@Composable
fun HistoryChart(readings: List<HistoryDb.Reading>, from: Long, to: Long, modifier: Modifier = Modifier) {
    // One touch marks the same moment on both panels, so a reading is never half-answered.
    var selectedTs by remember { mutableStateOf<Long?>(null) }
    Column(modifier) {
        ChartPanel(
            title = "Температура",
            readings = readings,
            from = from,
            to = to,
            valueOf = { it.temp },
            lineColor = MaterialTheme.colorScheme.primary,
            axisFormat = { String.format(Locale.getDefault(), "%.1f", it) },
            readoutFormat = { String.format(Locale.getDefault(), "%.2f °C", it) },
            selectedTs = selectedTs,
            onSelect = { selectedTs = it },
            modifier = Modifier.weight(1f).fillMaxSize().padding(bottom = 6.dp),
        )
        ChartPanel(
            title = "Влажность",
            readings = readings,
            from = from,
            to = to,
            valueOf = { it.hum },
            lineColor = MaterialTheme.colorScheme.tertiary,
            axisFormat = { String.format(Locale.getDefault(), "%.0f", it) },
            readoutFormat = { String.format(Locale.getDefault(), "%.1f %%", it) },
            selectedTs = selectedTs,
            onSelect = { selectedTs = it },
            modifier = Modifier.weight(1f).fillMaxSize(),
        )
    }
}

@Composable
private fun ChartPanel(
    title: String,
    readings: List<HistoryDb.Reading>,
    from: Long,
    to: Long,
    valueOf: (HistoryDb.Reading) -> Float,
    lineColor: Color,
    axisFormat: (Float) -> String,
    readoutFormat: (Float) -> String,
    selectedTs: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A temperature-only sensor stores no humidity, so each panel plots only the rows it has a value for.
    val points = readings.filter { !valueOf(it).isNaN() }
    val livePoints by rememberUpdatedState(points)
    val marked = selectedTs?.let { ts -> points.minByOrNull { abs(it.ts - ts) } }
    // Within a day the time of day is what identifies a reading; over longer spans the date does.
    val stampFormat = remember(from, to) {
        if (to - from <= 36L * 3600_000L) SimpleDateFormat("HH:mm", Locale.getDefault())
        else SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
    }

    ElevatedCard(modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            // Fixed height so the plot does not resize when the readout appears.
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = lineColor)
                if (marked != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        readoutFormat(valueOf(marked)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stampFormat.format(Date(marked.ts)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                if (points.isEmpty()) {
                    Text(
                        "Нет данных за выбранный период",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    val gridColor = MaterialTheme.colorScheme.outlineVariant
                    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val hairlineColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val surfaceColor = MaterialTheme.colorScheme.surface
                    val fillColor = lineColor.copy(alpha = 0.15f)
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            // Keyed on the window only: re-keying on the list would drop an in-progress drag.
                            .pointerInput(from, to) {
                                val left = PlotLeftPad.toPx()
                                val right = (size.width - PlotRightPad.toPx()).coerceAtLeast(left + 1f)
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    onSelect(touchedTs(down.position.x, left, right, from, to, livePoints))
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (!change.pressed) break
                                        onSelect(touchedTs(change.position.x, left, right, from, to, livePoints))
                                        change.consume()
                                    }
                                    onSelect(null)
                                }
                            },
                    ) {
                        val values = points.map(valueOf)
                        var min = values.min()
                        var max = values.max()
                        val pad = (max - min).let { if (it <= 0f) 1f else it * 0.15f }
                        min -= pad
                        max += pad

                        val l = PlotLeftPad.toPx()
                        val r = size.width - PlotRightPad.toPx()
                        val t = PlotTopPad.toPx()
                        val b = size.height - PlotBottomPad.toPx()
                        val span = (to - from).coerceAtLeast(1L).toFloat()

                        val labelPaint = Paint().apply {
                            color = labelColor.toArgb()
                            textSize = 11.sp.toPx()
                            isAntiAlias = true
                        }

                        for (i in 0..4) {
                            val y = t + (b - t) * i / 4f
                            drawLine(gridColor, Offset(l, y), Offset(r, y), strokeWidth = 1f)
                            val v = max - (max - min) * i / 4f
                            drawContext.canvas.nativeCanvas.drawText(axisFormat(v), r + 6.dp.toPx(), y + 4.dp.toPx(), labelPaint)
                        }

                        fun xOf(ts: Long) = l + (r - l) * (((ts - from).toFloat() / span).coerceIn(0f, 1f))
                        fun yOf(v: Float) = b - (v - min) / (max - min) * (b - t)

                        val path = Path()
                        val fillPath = Path()
                        points.forEachIndexed { index, reading ->
                            val x = xOf(reading.ts)
                            val y = yOf(valueOf(reading))
                            if (index == 0) {
                                path.moveTo(x, y)
                                fillPath.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                        }
                        fillPath.lineTo(xOf(points.last().ts), b)
                        fillPath.lineTo(xOf(points.first().ts), b)
                        fillPath.close()
                        drawPath(fillPath, color = fillColor, style = Fill)
                        drawPath(path, color = lineColor, style = Stroke(width = 3.dp.toPx()))

                        val last = points.last()
                        val lx = xOf(last.ts)
                        val ly = yOf(valueOf(last))
                        drawCircle(surfaceColor, radius = 6.dp.toPx(), center = Offset(lx, ly))
                        drawCircle(lineColor, radius = 6.dp.toPx(), center = Offset(lx, ly), style = Stroke(width = 2.5.dp.toPx()))

                        if (marked != null) {
                            val mx = xOf(marked.ts)
                            val my = yOf(valueOf(marked))
                            drawLine(hairlineColor, Offset(mx, t), Offset(mx, b), strokeWidth = 1.dp.toPx())
                            drawCircle(surfaceColor, radius = 7.dp.toPx(), center = Offset(mx, my))
                            drawCircle(lineColor, radius = 7.dp.toPx(), center = Offset(mx, my), style = Stroke(width = 3.dp.toPx()))
                        }
                    }
                }
            }
        }
    }
}

// Snaps the touch to the nearest recorded reading, so aiming anywhere near a point is enough.
private fun touchedTs(x: Float, left: Float, right: Float, from: Long, to: Long, points: List<HistoryDb.Reading>): Long? {
    if (points.isEmpty()) return null
    val fraction = ((x - left) / (right - left)).coerceIn(0f, 1f)
    val target = from + ((to - from) * fraction).toLong()
    return points.minByOrNull { abs(it.ts - target) }?.ts
}
