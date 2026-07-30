package com.liwenzhong.autoledger.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextFormatterTest {
    @Test
    fun `normalizes trims deduplicates and bounds OCR lines`() {
        val longLine = "商".repeat(240)
        val text = buildString {
            appendLine("  微信支付  ")
            appendLine()
            appendLine("微信支付")
            appendLine(longLine)
            repeat(90) { appendLine("第${it}行") }
        }

        val lines = OcrTextFormatter.lines(text)

        assertEquals("微信支付", lines.first())
        assertEquals(200, lines[1].length)
        assertEquals(80, lines.size)
    }
}
