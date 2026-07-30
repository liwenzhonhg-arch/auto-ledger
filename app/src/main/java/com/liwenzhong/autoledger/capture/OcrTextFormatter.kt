package com.liwenzhong.autoledger.capture

internal object OcrTextFormatter {
    private const val MAX_LINES = 80
    private const val MAX_LINE_LENGTH = 200

    fun lines(text: String): List<String> =
        text.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { it.take(MAX_LINE_LENGTH) }
            .distinct()
            .take(MAX_LINES)
            .toList()
}
