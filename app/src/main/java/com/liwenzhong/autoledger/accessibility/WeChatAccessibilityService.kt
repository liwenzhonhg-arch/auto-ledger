package com.liwenzhong.autoledger.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liwenzhong.autoledger.MainActivity
import com.liwenzhong.autoledger.R
import com.liwenzhong.autoledger.capture.OfflineOcrRecognizer
import com.liwenzhong.autoledger.capture.CaptureDiagnostics
import com.liwenzhong.autoledger.capture.CaptureOutcome
import com.liwenzhong.autoledger.capture.CaptureSource
import com.liwenzhong.autoledger.parser.ScreenRejection
import com.liwenzhong.autoledger.parser.TransactionCandidate
import com.liwenzhong.autoledger.parser.WeChatScreenParser
import com.liwenzhong.autoledger.repository.IngestResult
import com.liwenzhong.autoledger.repository.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val WECHAT_PACKAGE = "com.tencent.mm"
private const val MAX_LINES = 80
private const val MAX_LINE_LENGTH = 200
private const val SETTLE_DELAY_MILLIS = 500L
private const val SETTINGS_NAME = "accessibility_capture"
private const val AUTO_CAPTURE_KEY = "wechat_auto_capture"
private const val CAPTURE_LOG_TAG = "AutoLedgerCapture"
private const val LEDGER_CHANNEL_ID = "automatic_ledger"

internal object AccessibilityCaptureGate {
    fun shouldCapture(
        eventPackageName: String,
        windowPackageName: String,
        armed: Boolean,
        autoCaptureEnabled: Boolean,
    ): Boolean = eventPackageName == WECHAT_PACKAGE &&
        windowPackageName == WECHAT_PACKAGE &&
        (armed || autoCaptureEnabled)
}

object AccessibilityAutoCaptureSettings {
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
            .getBoolean(AUTO_CAPTURE_KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(AUTO_CAPTURE_KEY, enabled)
            .apply()
    }
}

object AccessibilityServiceStatus {
    var running by mutableStateOf(false)
        private set

    internal fun connected() {
        running = true
    }

    internal fun disconnected() {
        running = false
    }
}

data class AccessibilitySample(
    val capturedAtMillis: Long,
    val windowClass: String?,
    val lines: List<String>,
)

object AccessibilitySampleStore {
    var armed by mutableStateOf(false)
        private set
    var latest by mutableStateOf<AccessibilitySample?>(null)
        private set

    fun arm() {
        latest = null
        armed = true
    }

    fun cancel() {
        armed = false
    }

    fun clear() {
        armed = false
        latest = null
    }

    fun stopOnAppResume() {
        armed = false
    }

    internal fun capture(sample: AccessibilitySample) {
        if (armed) {
            latest = sample
            armed = false
        }
    }
}

class WeChatAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate +
        CoroutineExceptionHandler { _, _ -> report(CaptureOutcome.CAPTURE_FAILED) })
    private val ocrExecutor = Executors.newSingleThreadExecutor()
    private val repository by lazy { LedgerRepository(applicationContext) }
    private var pendingCapture: Job? = null
    private var screenshotInFlight = false
    private var screenshotRequestedWhileBusy = false
    private var destroyed = false

    private fun report(outcome: CaptureOutcome) = CaptureDiagnostics.report(CaptureSource.SCREEN, outcome)

    private fun captureStillAllowed(): Boolean = !destroyed &&
        !getSystemService(KeyguardManager::class.java).isDeviceLocked &&
        (AccessibilityAutoCaptureSettings.isEnabled(this) || AccessibilitySampleStore.armed) &&
        rootInActiveWindow?.packageName?.toString() == WECHAT_PACKAGE

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceStatus.connected()
        Log.d(CAPTURE_LOG_TAG, "service_connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventPackageName = event.packageName?.toString() ?: return
        val autoCaptureEnabled = AccessibilityAutoCaptureSettings.isEnabled(this)
        if (eventPackageName != WECHAT_PACKAGE ||
            (!AccessibilitySampleStore.armed && !autoCaptureEnabled)
        ) return

        if (pendingCapture?.isActive == true) return
        pendingCapture = serviceScope.launch {
            delay(SETTLE_DELAY_MILLIS)
            captureSettledWindow(eventPackageName)
        }
    }

    private fun captureSettledWindow(eventPackageName: String) {
        if (destroyed || getSystemService(KeyguardManager::class.java).isDeviceLocked) return
        val autoCaptureEnabled = AccessibilityAutoCaptureSettings.isEnabled(this)
        if (!autoCaptureEnabled && !AccessibilitySampleStore.armed) return
        val root = rootInActiveWindow ?: run {
            report(CaptureOutcome.NO_WINDOW)
            Log.d(CAPTURE_LOG_TAG, "capture_skipped no_root")
            return
        }
        val windowPackageName = root.packageName?.toString() ?: run {
            Log.d(CAPTURE_LOG_TAG, "capture_skipped no_window_package")
            return
        }
        if (!AccessibilityCaptureGate.shouldCapture(
                eventPackageName = eventPackageName,
                windowPackageName = windowPackageName,
                armed = AccessibilitySampleStore.armed,
                autoCaptureEnabled = autoCaptureEnabled,
            )) {
            Log.d(CAPTURE_LOG_TAG, "capture_skipped gate window_is_wechat=${windowPackageName == WECHAT_PACKAGE}")
            return
        }

        val lines = linkedSetOf<String>()
        report(CaptureOutcome.RECEIVED)
        collectVisibleText(root, lines)
        if (lines.isNotEmpty()) {
            val sample = AccessibilitySample(
                capturedAtMillis = System.currentTimeMillis(),
                windowClass = root.className?.toString(),
                lines = lines.toList(),
            )
            val candidate = WeChatScreenParser.parse(sample.lines, sample.capturedAtMillis)
            Log.d(CAPTURE_LOG_TAG, "text_capture lines=${lines.size} transaction=${candidate != null}")
            if (candidate != null) {
                AccessibilitySampleStore.capture(sample)
                if (autoCaptureEnabled) ingest(candidate) else report(CaptureOutcome.SAMPLE_ONLY)
                return
            }
        }

        requestScreenshot()
    }

    private fun requestScreenshot() {
        if (!captureStillAllowed()) {
            report(CaptureOutcome.PAUSED)
            return
        }
        if (screenshotInFlight) {
            screenshotRequestedWhileBusy = true
            return
        }

        screenshotInFlight = true
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    if (!captureStillAllowed()) {
                        screenshot.hardwareBuffer.close()
                        report(CaptureOutcome.PAUSED)
                        finishScreenshot("screenshot_skipped device_locked")
                        return
                    }
                    val bitmap = runCatching { screenshot.toSoftwareBitmap() }.getOrNull()
                    if (bitmap == null) {
                        report(CaptureOutcome.CAPTURE_FAILED)
                        finishScreenshot("screenshot_failed bitmap_unavailable")
                        return
                    }
                    runCatching {
                        report(CaptureOutcome.RECOGNIZING)
                        ocrExecutor.execute {
                            val lines = runCatching {
                                try {
                                    OfflineOcrRecognizer(applicationContext).recognize(bitmap)
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                            mainExecutor.execute {
                                try {
                                    if (!captureStillAllowed()) report(CaptureOutcome.PAUSED)
                                    else lines.onSuccess(::handleOcrLines)
                                        .onFailure { report(CaptureOutcome.OCR_FAILED) }
                                } catch (_: Exception) {
                                    report(CaptureOutcome.OCR_FAILED)
                                } finally {
                                    finishScreenshot()
                                }
                            }
                        }
                    }.onFailure {
                        bitmap.recycle()
                        report(CaptureOutcome.OCR_FAILED)
                        finishScreenshot("ocr_executor_unavailable")
                    }
                }

                override fun onFailure(errorCode: Int) {
                    report(CaptureOutcome.CAPTURE_FAILED)
                    finishScreenshot("screenshot_failed code=$errorCode")
                }
            },
            )
        }.onFailure {
            report(CaptureOutcome.CAPTURE_FAILED)
            finishScreenshot("screenshot_failed request_rejected")
        }
    }

    private fun handleOcrLines(lines: List<String>) {
        val sample = AccessibilitySample(
            capturedAtMillis = System.currentTimeMillis(),
            windowClass = "accessibility-screenshot-ocr",
            lines = lines,
        )
        AccessibilitySampleStore.capture(sample)
        val result = WeChatScreenParser.inspect(lines, sample.capturedAtMillis)
        val candidate = result.candidate
        Log.d(CAPTURE_LOG_TAG, "ocr_complete lines=${lines.size} transaction=${candidate != null}")
        if (AccessibilityAutoCaptureSettings.isEnabled(this) && candidate != null) {
            ingest(candidate)
        } else {
            report(when (result.rejection) {
                ScreenRejection.MULTIPLE_BILLS -> CaptureOutcome.MULTIPLE_BILLS
                ScreenRejection.AMOUNT_UNCLEAR -> CaptureOutcome.AMOUNT_UNCLEAR
                ScreenRejection.NOT_TRANSACTION -> CaptureOutcome.NOT_TRANSACTION
                null -> CaptureOutcome.SAMPLE_ONLY
            })
        }
    }

    private fun ingest(candidate: TransactionCandidate) {
        serviceScope.launch(Dispatchers.IO) {
            if (!AccessibilityAutoCaptureSettings.isEnabled(this@WeChatAccessibilityService)) {
                report(CaptureOutcome.PAUSED)
                return@launch
            }
            if (repository.ingest(candidate) == IngestResult.ADDED) {
                // The record is already saved; a disabled notification must not crash the service.
                mainExecutor.execute { runCatching { notifyPendingTransaction() } }
            }
        }
    }

    private fun notifyPendingTransaction() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                LEDGER_CHANNEL_ID,
                "自动记账确认",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicNotification = Notification.Builder(this, LEDGER_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("检测到一笔微信交易")
            .setContentText("解锁后打开 Auto Ledger 查看")
            .build()
        manager.notify(
            System.currentTimeMillis().toInt(),
            Notification.Builder(this, LEDGER_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("检测到一笔微信交易")
                .setContentText("打开 Auto Ledger 确认金额、对象和分类")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicNotification)
                .build(),
        )
    }

    private fun finishScreenshot(logMessage: String? = null) {
        logMessage?.let { Log.d(CAPTURE_LOG_TAG, it) }
        screenshotInFlight = false
        if (screenshotRequestedWhileBusy && !destroyed) {
            screenshotRequestedWhileBusy = false
            pendingCapture = serviceScope.launch {
                delay(SETTLE_DELAY_MILLIS)
                captureSettledWindow(WECHAT_PACKAGE)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityServiceStatus.disconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        destroyed = true
        pendingCapture?.cancel()
        serviceScope.cancel()
        // Let any accepted OCR task recycle the bitmap it owns.
        ocrExecutor.shutdown()
        AccessibilityServiceStatus.disconnected()
        super.onDestroy()
    }
}

private fun AccessibilityService.ScreenshotResult.toSoftwareBitmap(): Bitmap? {
    return try {
        val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace) ?: return null
        try {
            hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            hardwareBitmap.recycle()
        }
    } finally {
        hardwareBuffer.close()
    }
}

private fun collectVisibleText(node: AccessibilityNodeInfo, lines: MutableSet<String>) {
    if (lines.size >= MAX_LINES) return

    if (node.isVisibleToUser) sequenceOf(node.text, node.contentDescription)
        .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        .map { it.take(MAX_LINE_LENGTH) }
        .forEach(lines::add)

    for (index in 0 until node.childCount) {
        if (lines.size >= MAX_LINES) return
        node.getChild(index)?.let { collectVisibleText(it, lines) }
    }
}
