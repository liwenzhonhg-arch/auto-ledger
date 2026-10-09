package com.liwenzhong.autoledger.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class CaptureSource(val label: String) {
    NOTIFICATION("微信通知"), SCREEN("微信界面"), MANUAL("单次截图"),
}

enum class CaptureOutcome(val label: String) {
    RECEIVED("收到内容"), RECOGNIZING("正在识别"), SAVED("已保存 · 待确认"),
    DUPLICATE("重复忽略"), NOT_TRANSACTION("未识别交易"),
    MULTIPLE_BILLS("忽略多笔账单"), AMOUNT_UNCLEAR("金额不明确"),
    SAVE_FAILED("保存失败"), CAPTURE_FAILED("截图失败"), OCR_FAILED("识别失败"),
    NO_WINDOW("窗口不可读"), PAUSED("采集条件已变化"), SAMPLE_ONLY("仅查看样本"),
}

data class CaptureEvent(
    val source: CaptureSource,
    val outcome: CaptureOutcome,
    val atMillis: Long,
)

data class CaptureDiagnosticState(
    val latest: Map<CaptureSource, CaptureEvent> = emptyMap(),
    val recent: List<CaptureEvent> = emptyList(),
    val lastSaved: CaptureEvent? = null,
    val lastFailure: CaptureEvent? = null,
)

/** Only bounded status codes and timestamps; never text, amounts, images or exception messages. */
object CaptureDiagnostics {
    private val mutableState = MutableStateFlow(CaptureDiagnosticState())
    val state = mutableState.asStateFlow()

    fun report(source: CaptureSource, outcome: CaptureOutcome) {
        val event = CaptureEvent(source, outcome, System.currentTimeMillis())
        mutableState.update { previous -> reduceCaptureDiagnostics(previous, event) }
    }
}

internal fun reduceCaptureDiagnostics(
    previous: CaptureDiagnosticState,
    event: CaptureEvent,
): CaptureDiagnosticState {
    val transient = event.outcome == CaptureOutcome.RECEIVED || event.outcome == CaptureOutcome.RECOGNIZING
    val failed = event.outcome in setOf(
        CaptureOutcome.SAVE_FAILED, CaptureOutcome.CAPTURE_FAILED, CaptureOutcome.OCR_FAILED,
    )
    return previous.copy(
        latest = previous.latest + (event.source to event),
        recent = if (transient) previous.recent else (listOf(event) + previous.recent).take(12),
        lastSaved = if (event.outcome == CaptureOutcome.SAVED) event else previous.lastSaved,
        lastFailure = if (failed) event else previous.lastFailure,
    )
}
