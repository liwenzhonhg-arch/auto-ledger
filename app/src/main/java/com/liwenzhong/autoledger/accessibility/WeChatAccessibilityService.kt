package com.liwenzhong.autoledger.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val WECHAT_PACKAGE = "com.tencent.mm"
private const val MAX_LINES = 80
private const val MAX_LINE_LENGTH = 200

internal object AccessibilityCaptureGate {
    fun shouldCapture(eventPackageName: String, windowPackageName: String, armed: Boolean): Boolean =
        eventPackageName == WECHAT_PACKAGE && windowPackageName == WECHAT_PACKAGE && armed
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
        if (armed) latest = sample
    }
}

class WeChatAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventPackageName = event.packageName?.toString() ?: return
        if (!AccessibilitySampleStore.armed || eventPackageName != WECHAT_PACKAGE) return
        val root = rootInActiveWindow ?: return
        val windowPackageName = root.packageName?.toString() ?: return
        if (!AccessibilityCaptureGate.shouldCapture(
                eventPackageName,
                windowPackageName,
                AccessibilitySampleStore.armed,
            )
        ) {
            return
        }

        val lines = linkedSetOf<String>()
        collectVisibleText(root, lines)
        if (lines.isEmpty()) return

        AccessibilitySampleStore.capture(
            AccessibilitySample(
                capturedAtMillis = System.currentTimeMillis(),
                windowClass = root.className?.toString(),
                lines = lines.toList(),
            ),
        )
    }

    override fun onInterrupt() = Unit
}

private fun collectVisibleText(node: AccessibilityNodeInfo, lines: MutableSet<String>) {
    if (lines.size >= MAX_LINES) return

    sequenceOf(node.text, node.contentDescription)
        .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        .map { it.take(MAX_LINE_LENGTH) }
        .forEach(lines::add)

    for (index in 0 until node.childCount) {
        if (lines.size >= MAX_LINES) return
        node.getChild(index)?.let { collectVisibleText(it, lines) }
    }
}
