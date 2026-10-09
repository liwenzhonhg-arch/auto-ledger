package com.liwenzhong.autoledger.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
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
import com.liwenzhong.autoledger.capture.CaptureDiagnostics
import com.liwenzhong.autoledger.capture.CaptureOutcome
import com.liwenzhong.autoledger.capture.CaptureSource

private const val WECHAT_PACKAGE = "com.tencent.mm"

internal object NotificationCaptureGate {
    fun shouldCapture(packageName: String): Boolean = packageName == WECHAT_PACKAGE
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

object NotificationListenerStatus {
    var connected by mutableStateOf(false)
        private set
    var recoveryMessage by mutableStateOf<String?>(null)
        private set

    internal fun updateConnection(isConnected: Boolean) {
        connected = isConnected
        recoveryMessage = null
    }

    fun requestReconnect(context: Context) {
        val component = ComponentName(context, WeChatNotificationListenerService::class.java)
        val granted = context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(component)
        if (!shouldRequestNotificationReconnect(granted, connected)) return
        try {
            NotificationListenerService.requestRebind(component)
            recoveryMessage = "已请求重连，连接状态以系统回调为准。"
        } catch (_: SecurityException) {
            recoveryMessage = "重连未获允许，请检查通知访问设置。"
        } catch (_: IllegalStateException) {
            recoveryMessage = "系统暂时无法重连，请稍后重试。"
        }
    }

    internal fun reportReplayFailure() {
        recoveryMessage = "已连接，但补读现有通知失败，请重新检查通知访问设置。"
    }
}

internal fun shouldRequestNotificationReconnect(granted: Boolean, connected: Boolean): Boolean =
    granted && !connected

class WeChatNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy { LedgerRepository(applicationContext) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationListenerStatus.updateConnection(true)
        try {
            activeNotifications.orEmpty().forEach(::onNotificationPosted)
        } catch (_: SecurityException) {
            NotificationListenerStatus.reportReplayFailure()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationListenerStatus.updateConnection(false)
        NotificationListenerStatus.requestReconnect(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        if (!NotificationCaptureGate.shouldCapture(sbn.packageName)) {
            return
        }

        val extras = notification.extras
        CaptureDiagnostics.report(CaptureSource.NOTIFICATION, CaptureOutcome.RECEIVED)
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
        captureNotificationSample(sample)
        serviceScope.launch { repository.ingest(sample) }
    }

    override fun onDestroy() {
        NotificationListenerStatus.updateConnection(false)
        serviceScope.cancel()
        super.onDestroy()
    }
}

private fun android.os.Bundle.text(key: String): String? =
    getCharSequence(key)?.toString()?.trim()?.takeIf(String::isNotEmpty)
