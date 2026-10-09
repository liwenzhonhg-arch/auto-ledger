package com.liwenzhong.autoledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class LedgerSymbol { BOOK, CHART, CAPTURE, PLUS, CHECK, BACK, ARROW, CATEGORY }

@Composable
internal fun LedgerIcon(
    symbol: LedgerSymbol,
    modifier: Modifier = Modifier,
    description: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Canvas(modifier.size(24.dp).semantics { if (description != null) contentDescription = description }) {
        val unit = size.minDimension / 24f
        val stroke = 1.8f * unit
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
            tint, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), stroke, StrokeCap.Round,
        )
        when (symbol) {
            LedgerSymbol.BOOK -> {
                drawRoundRect(tint, Offset(5f * unit, 3f * unit), Size(14f * unit, 18f * unit),
                    CornerRadius(3f * unit), style = Stroke(stroke))
                line(9f, 8f, 15f, 8f); line(9f, 12f, 15f, 12f); line(9f, 16f, 12f, 16f)
            }
            LedgerSymbol.CHART -> {
                line(5f, 19f, 5f, 12f); line(12f, 19f, 12f, 5f); line(19f, 19f, 19f, 9f)
            }
            LedgerSymbol.CAPTURE -> {
                drawCircle(tint, 8f * unit, style = Stroke(stroke))
                drawCircle(tint, 3f * unit)
                line(12f, 1f, 12f, 5f); line(12f, 19f, 12f, 23f)
            }
            LedgerSymbol.PLUS -> { line(5f, 12f, 19f, 12f); line(12f, 5f, 12f, 19f) }
            LedgerSymbol.CHECK -> { line(5f, 12f, 10f, 17f); line(10f, 17f, 19f, 7f) }
            LedgerSymbol.BACK -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
            LedgerSymbol.ARROW -> { line(9f, 6f, 15f, 12f); line(15f, 12f, 9f, 18f) }
            LedgerSymbol.CATEGORY -> {
                val path = Path().apply {
                    moveTo(5f * unit, 4f * unit); lineTo(14f * unit, 4f * unit)
                    lineTo(21f * unit, 11f * unit); lineTo(11f * unit, 21f * unit)
                    lineTo(4f * unit, 14f * unit); close()
                }
                drawPath(path, tint, style = Stroke(stroke))
                drawCircle(tint, 1.4f * unit, Offset(9f * unit, 9f * unit))
            }
        }
    }
}
