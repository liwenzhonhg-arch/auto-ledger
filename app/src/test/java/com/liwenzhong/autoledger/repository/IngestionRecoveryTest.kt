package com.liwenzhong.autoledger.repository

import com.liwenzhong.autoledger.capture.CaptureDiagnostics
import com.liwenzhong.autoledger.capture.CaptureOutcome
import com.liwenzhong.autoledger.capture.CaptureSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class IngestionRecoveryTest {
    @Test fun failedWriteIsReportedAndNextWriteCanSucceed() = runBlocking {
        assertEquals(IngestResult.FAILED, recordIngestion(CaptureSource.NOTIFICATION) {
            throw IllegalStateException("synthetic database failure")
        })
        assertEquals(CaptureOutcome.SAVE_FAILED,
            CaptureDiagnostics.state.value.latest[CaptureSource.NOTIFICATION]?.outcome)
        assertEquals(IngestResult.ADDED, recordIngestion(CaptureSource.NOTIFICATION) { IngestResult.ADDED })
        assertEquals(CaptureOutcome.SAVED,
            CaptureDiagnostics.state.value.latest[CaptureSource.NOTIFICATION]?.outcome)
    }

    @Test fun lifecycleCancellationIsNotReportedAsDatabaseFailure() = runBlocking {
        try {
            recordIngestion(CaptureSource.SCREEN) { throw CancellationException() }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
