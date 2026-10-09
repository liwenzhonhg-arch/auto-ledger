package com.liwenzhong.autoledger.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class OcrCaptureStatus {
    IDLE,
    PREPARING,
    CAPTURING,
    RECOGNIZING,
    SUCCESS,
    ERROR,
}

data class OcrCaptureState(
    val status: OcrCaptureStatus = OcrCaptureStatus.IDLE,
    val capturedAtMillis: Long? = null,
    val lines: List<String> = emptyList(),
    val error: String? = null,
)

object OcrCaptureStore {
    var state by mutableStateOf(OcrCaptureState())
        private set

    fun prepare() {
        state = OcrCaptureState(status = OcrCaptureStatus.PREPARING)
    }

    internal fun capturing() {
        CaptureDiagnostics.report(CaptureSource.MANUAL, CaptureOutcome.RECEIVED)
        state = OcrCaptureState(status = OcrCaptureStatus.CAPTURING)
    }

    internal fun recognizing() {
        CaptureDiagnostics.report(CaptureSource.MANUAL, CaptureOutcome.RECOGNIZING)
        state = OcrCaptureState(status = OcrCaptureStatus.RECOGNIZING)
    }

    internal fun success(lines: List<String>) {
        CaptureDiagnostics.report(CaptureSource.MANUAL, CaptureOutcome.SAMPLE_ONLY)
        state = OcrCaptureState(
            status = OcrCaptureStatus.SUCCESS,
            capturedAtMillis = System.currentTimeMillis(),
            lines = lines,
        )
    }

    fun fail(message: String) {
        CaptureDiagnostics.report(CaptureSource.MANUAL, CaptureOutcome.CAPTURE_FAILED)
        state = OcrCaptureState(status = OcrCaptureStatus.ERROR, error = message)
    }

    fun clear() {
        state = OcrCaptureState()
    }
}
