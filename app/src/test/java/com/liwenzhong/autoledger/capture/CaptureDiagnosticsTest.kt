package com.liwenzhong.autoledger.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureDiagnosticsTest {
    @Test fun laterIgnoredPagesDoNotHideLastSavedOrFailureAndHistoryIsBounded() {
        val saved = CaptureEvent(CaptureSource.NOTIFICATION, CaptureOutcome.SAVED, 1L)
        val failed = CaptureEvent(CaptureSource.SCREEN, CaptureOutcome.OCR_FAILED, 2L)
        var state = reduceCaptureDiagnostics(CaptureDiagnosticState(), saved)
        state = reduceCaptureDiagnostics(state, failed)
        repeat(20) {
            state = reduceCaptureDiagnostics(state,
                CaptureEvent(CaptureSource.SCREEN, CaptureOutcome.NOT_TRANSACTION, 3L + it))
        }
        assertEquals(saved, state.lastSaved)
        assertEquals(failed, state.lastFailure)
        assertEquals(12, state.recent.size)
        assertEquals(saved, state.latest[CaptureSource.NOTIFICATION])
    }
}
