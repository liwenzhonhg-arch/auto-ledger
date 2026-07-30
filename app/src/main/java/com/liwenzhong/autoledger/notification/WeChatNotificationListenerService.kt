package com.liwenzhong.autoledger.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liwenzhong.autoledger.repository.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val WECHAT_PACKAGE = "com.tencent.mm"

internal object NotificationCaptureGate {
    fun shouldCapture(packageName: String, armed: Boolean): Boolean =
        packageName == WECHAT_PACKAGE && armed
}

data class NotificationSample(
    val postedAtMillis: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val textLines: List<String>,
)

object NotificationSampleStore {
    var armed by mutableStateOf(false)
        private set
    var latest by mutableStateOf<NotificationSample?>(null)
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

    internal fun capture(sample: NotificationSample): Boolean {
        if (!armed) return false
        latest = sample
        armed = false
        return true
    }
}

internal fun captureNotificationSample(sample: NotificationSample): Boolean =
    NotificationSampleStore.capture(sample)

class WeChatNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy { LedgerRepository(applicationContext) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        if (!NotificationCaptureGate.shouldCapture(sbn.packageName, NotificationSampleStore.armed)) {
            return
        }

        val extras = notification.extras
        val sample = NotificationSample(
            postedAtMillis = sbn.postTime,
            title = extras.text(Notification.EXTRA_TITLE),
            text = extras.text(Notification.EXTRA_TEXT),
            bigText = extras.text(Notification.EXTRA_BIG_TEXT),
            subText = extras.text(Notification.EXTRA_SUB_TEXT),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
                .orEmpty(),
        )
        if (captureNotificationSample(sample)) {
            serviceScope.launch { repository.ingest(sample) }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

private fun android.os.Bundle.text(key: String): String? =
    getCharSequence(key)?.toString()?.trim()?.takeIf(String::isNotEmpty)
