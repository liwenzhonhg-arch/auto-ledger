package com.liwenzhong.autoledger

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liwenzhong.autoledger.accessibility.AccessibilitySampleStore
import com.liwenzhong.autoledger.accessibility.AccessibilityAutoCaptureSettings
import com.liwenzhong.autoledger.accessibility.AccessibilityServiceStatus
import com.liwenzhong.autoledger.accessibility.WeChatAccessibilityService
import com.liwenzhong.autoledger.capture.OcrCaptureStore
import com.liwenzhong.autoledger.capture.OneShotCaptureService
import com.liwenzhong.autoledger.notification.NotificationSample
import com.liwenzhong.autoledger.notification.NotificationSampleStore
import com.liwenzhong.autoledger.notification.WeChatNotificationListenerService
import com.liwenzhong.autoledger.notification.captureNotificationSample
import com.liwenzhong.autoledger.repository.LedgerRepository
import com.liwenzhong.autoledger.ui.AutoLedgerApp
import com.liwenzhong.autoledger.ui.AutoLedgerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var notificationAccessGranted by mutableStateOf(false)
    private var accessibilityAccessGranted by mutableStateOf(false)
    private var accessibilityAutoCaptureEnabled by mutableStateOf(false)
    private var pendingWeChatLaunch: Intent? = null
    private val ledgerRepository by lazy { LedgerRepository(applicationContext) }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            pendingWeChatLaunch = null
            OcrCaptureStore.fail("你取消了屏幕捕获")
            return@registerForActivityResult
        }

        OneShotCaptureService.start(this, result.resultCode, data)
        pendingWeChatLaunch?.let(::startActivity)
        pendingWeChatLaunch = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        launchScreenCaptureConsent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accessibilityAutoCaptureEnabled = AccessibilityAutoCaptureSettings.isEnabled(this)
        enableEdgeToEdge()
        setContent {
            AutoLedgerTheme {
                AutoLedgerApp(
                    notificationAccessGranted = notificationAccessGranted,
                    accessibilityAccessGranted = accessibilityAccessGranted,
                    accessibilityAutoCaptureEnabled = accessibilityAutoCaptureEnabled,
                    accessibilityServiceRunning = AccessibilityServiceStatus.running,
                    onOpenNotificationSettings = ::openNotificationSettings,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    onAccessibilityAutoCaptureChanged = ::updateAccessibilityAutoCapture,
                    onOpenWeChat = ::openWeChat,
                    onStartOcrCapture = ::startOcrCapture,
                    isDebugBuild = isDebuggable,
                    onSimulateNotification = ::simulateWeChatPaymentNotification,
                    pendingTransactions = ledgerRepository.pending,
                    confirmedTransactions = ledgerRepository.confirmed,
                    onConfirmCandidate = ledgerRepository::confirm,
                    onClearSyntheticTransactions = ledgerRepository::deleteSyntheticTransactions,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AccessibilitySampleStore.stopOnAppResume()
        accessibilityAutoCaptureEnabled = AccessibilityAutoCaptureSettings.isEnabled(this)
        notificationAccessGranted = getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(
                ComponentName(this, WeChatNotificationListenerService::class.java),
            )
        val component = ComponentName(this, WeChatAccessibilityService::class.java)
        accessibilityAccessGranted = getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == component }
    }

    private fun openNotificationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun updateAccessibilityAutoCapture(enabled: Boolean) {
        AccessibilityAutoCaptureSettings.setEnabled(this, enabled)
        accessibilityAutoCaptureEnabled = enabled
        if (!enabled) AccessibilitySampleStore.cancel()
    }

    private fun openWeChat() {
        packageManager.getLaunchIntentForPackage("com.tencent.mm")?.let(::startActivity)
    }

    private fun simulateWeChatPaymentNotification() {
        if (!isDebuggable) return

        NotificationSampleStore.arm()
        val sample = NotificationSample(
            postedAtMillis = System.currentTimeMillis(),
            title = "微信支付",
            text = "付款成功：向测试商户支付 ￥12.34",
            bigText = null,
            subText = "模拟数据",
            textLines = emptyList(),
        )
        if (captureNotificationSample(sample)) {
            lifecycleScope.launch { ledgerRepository.ingest(sample) }
        }
    }

    private fun startOcrCapture() {
        val weChatLaunch = packageManager.getLaunchIntentForPackage("com.tencent.mm")
        if (weChatLaunch == null) {
            OcrCaptureStore.fail("未找到微信")
            return
        }
        pendingWeChatLaunch = weChatLaunch
        OcrCaptureStore.prepare()

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            launchScreenCaptureConsent()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun launchScreenCaptureConsent() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        screenCaptureLauncher.launch(
            manager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay(),
            ),
        )
    }

    private val isDebuggable: Boolean
        get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
