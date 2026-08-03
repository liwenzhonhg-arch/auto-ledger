package com.liwenzhong.autoledger.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liwenzhong.autoledger.R
import com.liwenzhong.autoledger.accessibility.AccessibilitySample
import com.liwenzhong.autoledger.accessibility.AccessibilitySampleStore
import com.liwenzhong.autoledger.capture.OcrCaptureState
import com.liwenzhong.autoledger.capture.OcrCaptureStatus
import com.liwenzhong.autoledger.capture.OcrCaptureStore
import com.liwenzhong.autoledger.notification.NotificationSample
import com.liwenzhong.autoledger.notification.NotificationSampleStore
import com.liwenzhong.autoledger.parser.TransactionCandidate
import com.liwenzhong.autoledger.parser.TransactionDirection
import com.liwenzhong.autoledger.parser.WeChatNotificationParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class PreviewTransaction(
    val category: String,
    val objectName: String,
    val amount: String,
    val sourceAndTime: String,
    val income: Boolean = false,
    val pending: Boolean = false,
)

@Composable
fun AutoLedgerApp(
    notificationAccessGranted: Boolean = false,
    accessibilityAccessGranted: Boolean = false,
    accessibilityAutoCaptureEnabled: Boolean = false,
    accessibilityServiceRunning: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    onAccessibilityAutoCaptureChanged: (Boolean) -> Unit = {},
    onOpenWeChat: () -> Unit = {},
    onStartOcrCapture: () -> Unit = {},
    isDebugBuild: Boolean = false,
    onSimulateNotification: () -> Unit = {},
    pendingTransactions: Flow<List<TransactionCandidate>> = flowOf(emptyList()),
    confirmedTransactions: Flow<List<TransactionCandidate>> = flowOf(emptyList()),
    onConfirmCandidate: suspend (String, Long?, String, String) -> Boolean =
        { _, _, _, _ -> false },
    onClearSyntheticTransactions: suspend () -> Int = { 0 },
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showPending by remember { mutableStateOf(false) }
    val pending by pendingTransactions.collectAsState(initial = emptyList())
    val confirmed by confirmedTransactions.collectAsState(initial = emptyList())
    val tabs = listOf("账目", "图表", "记一笔")

    Scaffold(
        bottomBar = {
            if (!showSettings && !showPending) {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Text(tab.first().toString(), fontWeight = FontWeight.Bold) },
                            label = { Text(tab) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            showSettings -> NotificationCapturePage(
                    notificationAccessGranted = notificationAccessGranted,
                    accessibilityAccessGranted = accessibilityAccessGranted,
                    accessibilityAutoCaptureEnabled = accessibilityAutoCaptureEnabled,
                    accessibilityServiceRunning = accessibilityServiceRunning,
                    onBack = { showSettings = false },
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onAccessibilityAutoCaptureChanged = onAccessibilityAutoCaptureChanged,
                    onOpenWeChat = onOpenWeChat,
                    onStartOcrCapture = onStartOcrCapture,
                    isDebugBuild = isDebugBuild,
                    onSimulateNotification = onSimulateNotification,
                    onClearSyntheticTransactions = onClearSyntheticTransactions,
                    modifier = Modifier.padding(padding),
                )
            showPending -> PendingConfirmationPage(
                pending = pending,
                onConfirmCandidate = onConfirmCandidate,
                onBack = { showPending = false },
                onConfirmed = { showPending = false },
                modifier = Modifier.padding(padding),
            )
            else -> when (selectedTab) {
                0 -> LedgerHome(
                    pending = pending,
                    confirmed = confirmed,
                    onPendingClick = { showPending = true },
                    onSettingsClick = { showSettings = true },
                    modifier = Modifier.padding(padding),
                )
                1 -> PlaceholderPage("图表", "接入真实账目后显示分类占比和收支趋势。", Modifier.padding(padding))
                else -> PlaceholderPage("手动记账", "自动识别不是重点时，也可以在这里补记一笔。", Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun LedgerHome(
    pending: List<TransactionCandidate>,
    confirmed: List<TransactionCandidate>,
    onPendingClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transactions = pending + confirmed
    val expenseCents = transactions
        .filter { it.direction == TransactionDirection.EXPENSE }
        .sumOf { it.amountCents ?: 0L }
    val incomeCents = transactions
        .filter { it.direction == TransactionDirection.INCOME }
        .sumOf { it.amountCents ?: 0L }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CrowBrandMark(Modifier.size(58.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Auto Ledger", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "看见每一笔开销",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onSettingsClick) {
                    Text("设置", fontWeight = FontWeight.Medium)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = CrowInk,
                ),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        formatCurrentMonth(),
                        color = CrowIvory.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "本月已支出",
                        color = CrowIvory,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        formatYuan(expenseCents),
                        color = CrowIvory,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "收入 ${formatYuan(incomeCents)}",
                            color = CrowIvory.copy(alpha = 0.68f),
                            fontWeight = FontWeight.Medium,
                        )
                        if (pending.isNotEmpty()) {
                            Button(
                                onClick = onPendingClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CrowAmber,
                                    contentColor = CrowInk,
                                ),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("待确认 ${pending.size} 笔", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("已全部确认", color = Color(0xFF86C5BA))
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("最近支出", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${transactions.size} 笔",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (transactions.isNotEmpty()) {
            items(transactions, key = TransactionCandidate::fingerprint) { candidate ->
                TransactionRow(candidate.toPreview(isPending = pending.any { it.fingerprint == candidate.fingerprint }))
            }
        } else {
            item {
                Text(
                    "还没有支出记录。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PendingConfirmationPage(
    pending: List<TransactionCandidate>,
    onConfirmCandidate: suspend (String, Long?, String, String) -> Boolean,
    onBack: () -> Unit,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val candidate = pending.firstOrNull()
    if (candidate == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("暂无待确认账目", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        return
    }

    var amountText by remember(candidate.fingerprint) {
        mutableStateOf(candidate.amountCents?.let(::formatYuanInput).orEmpty())
    }
    var counterparty by remember(candidate.fingerprint) { mutableStateOf(candidate.counterparty) }
    var category by remember(candidate.fingerprint) { mutableStateOf(candidate.category) }
    var error by remember(candidate.fingerprint) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            PageHeader("确认支出", onBack)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        if (candidate.direction == TransactionDirection.INCOME) {
                            "这笔收入需要确认"
                        } else {
                            "这笔支出需要确认"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "通知没有提供的对象或分类可以在下面修改。",
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f),
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = amountText,
                onValueChange = { if (it.length <= 15) amountText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("金额（元）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = counterparty,
                onValueChange = { if (it.length <= 80) counterparty = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("交易对象") },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = category,
                onValueChange = { if (it.length <= 20) category = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("分类") },
                singleLine = true,
            )
        }
        error?.let {
            item {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val amountCents = WeChatNotificationParser.parseYuanInput(amountText)
                    if (amountCents == null || amountCents <= 0L) {
                        error = "请输入有效金额，最多两位小数"
                    } else {
                        scope.launch {
                            if (
                                onConfirmCandidate(
                                    candidate.fingerprint,
                                    amountCents,
                                    counterparty,
                                    category,
                                )
                            ) {
                                onConfirmed()
                            } else {
                                error = "确认失败，请返回后重试"
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("确认入账")
            }
        }
    }
}

private fun TransactionCandidate.toPreview(isPending: Boolean): PreviewTransaction {
    val amountText = amountCents?.let {
        (if (direction == TransactionDirection.INCOME) "+" else "-") + formatYuan(it)
    } ?: "金额待确认"
    return PreviewTransaction(
        category = transactionCategoryLabel(category, direction),
        objectName = counterparty,
        amount = amountText,
        sourceAndTime = "$source · ${formatTransactionTime(occurredAtMillis)} · ${
            if (isPending) "待确认" else "已确认"
        }",
        income = direction == TransactionDirection.INCOME,
        pending = isPending,
    )
}

internal fun transactionCategoryLabel(
    category: String,
    direction: TransactionDirection,
): String = if (category == "未分类" && direction == TransactionDirection.EXPENSE) {
    "支出"
} else {
    category
}

private fun formatYuan(cents: Long): String =
    "¥${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

private fun formatYuanInput(cents: Long): String =
    "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

private fun formatTransactionTime(occurredAtMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(occurredAtMillis))

private fun formatCurrentMonth(): String =
    SimpleDateFormat("yyyy 年 M 月", Locale.getDefault()).format(Date())

@Composable
private fun NotificationCapturePage(
    notificationAccessGranted: Boolean,
    accessibilityAccessGranted: Boolean,
    accessibilityAutoCaptureEnabled: Boolean,
    accessibilityServiceRunning: Boolean,
    onBack: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onAccessibilityAutoCaptureChanged: (Boolean) -> Unit,
    onOpenWeChat: () -> Unit,
    onStartOcrCapture: () -> Unit,
    isDebugBuild: Boolean,
    onSimulateNotification: () -> Unit,
    onClearSyntheticTransactions: suspend () -> Int,
    modifier: Modifier = Modifier,
) {
    val notificationArmed = NotificationSampleStore.armed
    val notificationSample = NotificationSampleStore.latest
    val accessibilityArmed = AccessibilitySampleStore.armed
    val accessibilitySample = AccessibilitySampleStore.latest
    val ocrState = OcrCaptureStore.state
    val scope = rememberCoroutineScope()
    var cleanupMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            PageHeader("采集与权限", onBack)
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("通知访问", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (notificationAccessGranted) "已允许" else "未允许",
                        color = if (notificationAccessGranted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    if (notificationAccessGranted) {
                        Text(
                            "微信通知会在后台自动解析，无需打开应用或手动开始采集。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = onOpenNotificationSettings) {
                            Text("管理通知访问")
                        }
                    } else {
                        Text(
                            "系统设置中只需开启“Auto Ledger 通知采集”。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onOpenNotificationSettings) {
                            Text("打开通知访问设置")
                        }
                    }
                }
            }
        }

        if (notificationAccessGranted || isDebugBuild) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (notificationAccessGranted) {
                            Text("查看原始通知样本", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (notificationArmed) {
                                    "正在等待下一条微信通知；后台自动解析仍然有效。"
                                } else {
                                    "仅供调试通知结构，原始通知不写入数据库。"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (notificationArmed) {
                                OutlinedButton(onClick = NotificationSampleStore::cancel) {
                                    Text("取消等待")
                                }
                            } else {
                                Button(onClick = NotificationSampleStore::arm) {
                                    Text(if (notificationSample == null) "采集下一条微信通知" else "重新采集")
                                }
                            }
                        }
                        if (isDebugBuild) {
                            OutlinedButton(onClick = onSimulateNotification) {
                                Text("模拟微信付款通知")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val count = onClearSyntheticTransactions()
                                        cleanupMessage = if (count > 0) {
                                            "已清除 $count 条模拟账目"
                                        } else {
                                            "没有需要清除的模拟账目"
                                        }
                                    }
                                },
                            ) {
                                Text("清除模拟账目")
                            }
                            cleanupMessage?.let {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                )
                            }
                            Text(
                                "仅 Debug 版可用，内容为固定虚构数据。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }

        notificationSample?.let {
            item {
                NotificationSampleCard(it)
            }
            item {
                OutlinedButton(onClick = NotificationSampleStore::clear) {
                    Text("清除内存中的样本")
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("微信界面只读访问", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (accessibilityAccessGranted) "已允许" else "未允许",
                        color = if (accessibilityAccessGranted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        when {
                            !accessibilityAccessGranted -> "授权后仍需在应用内开启自动记账。"
                            accessibilityServiceRunning -> "只读服务正在运行。"
                            else -> "已授权，但服务当前未运行。"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("微信自动记账", fontWeight = FontWeight.Medium)
                            Text(
                                "开启后持续读取微信交易页面；不会点击、输入或滚动。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                        Switch(
                            checked = accessibilityAutoCaptureEnabled,
                            onCheckedChange = onAccessibilityAutoCaptureChanged,
                            enabled = accessibilityAccessGranted,
                        )
                    }
                    if (!accessibilityAccessGranted) {
                        Button(onClick = onOpenAccessibilitySettings) {
                            Text("打开无障碍设置")
                        }
                    }
                }
            }
        }

        if (accessibilityAccessGranted) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("采集微信交易页面", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (accessibilityArmed) {
                                "正在读取微信当前可见文字。完成交易后直接切回 Auto Ledger。"
                            } else {
                                "开始后会打开微信；采集结果只保存在进程内存中。"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (accessibilityArmed) {
                            OutlinedButton(onClick = AccessibilitySampleStore::cancel) {
                                Text("取消采集")
                            }
                        } else {
                            Button(
                                onClick = {
                                    NotificationSampleStore.cancel()
                                    AccessibilitySampleStore.arm()
                                    onOpenWeChat()
                                },
                            ) {
                                Text(if (accessibilitySample == null) "开始并打开微信" else "重新采集")
                            }
                        }
                    }
                }
            }
        }

        accessibilitySample?.let {
            item {
                AccessibilitySampleCard(it)
            }
            item {
                OutlinedButton(onClick = AccessibilitySampleStore::clear) {
                    Text("清除内存中的界面样本")
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("单次屏幕 OCR", fontWeight = FontWeight.SemiBold)
                    Text(
                        when (ocrState.status) {
                            OcrCaptureStatus.PREPARING -> "等待系统屏幕共享授权。"
                            OcrCaptureStatus.CAPTURING -> "已返回微信，正在截取一帧。"
                            OcrCaptureStatus.RECOGNIZING -> "投屏已停止，正在本地识别；识别后立即销毁图像。"
                            OcrCaptureStatus.ERROR -> ocrState.error ?: "采集失败"
                            else -> "先停留在微信支付结果或账单详情页，再切回这里开始采集。"
                        },
                        color = if (ocrState.status == OcrCaptureStatus.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Button(
                        onClick = onStartOcrCapture,
                        enabled = ocrState.status !in setOf(
                            OcrCaptureStatus.PREPARING,
                            OcrCaptureStatus.CAPTURING,
                            OcrCaptureStatus.RECOGNIZING,
                        ),
                    ) {
                        Text(if (ocrState.status == OcrCaptureStatus.SUCCESS) "重新截取" else "截取当前微信页面")
                    }
                }
            }
        }

        if (ocrState.status == OcrCaptureStatus.SUCCESS) {
            item {
                OcrSampleCard(ocrState)
            }
            item {
                OutlinedButton(onClick = OcrCaptureStore::clear) {
                    Text("清除内存中的 OCR 结果")
                }
            }
        }

        item {
            Text(
                "当前只采集微信。屏幕捕获每次都需要你主动触发；只处理一帧，不保存截图，也不会点击或滚动微信。",
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun OcrSampleCard(state: OcrCaptureState) {
    val capturedAt = state.capturedAtMillis?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
    } ?: "未知"

    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("本地 OCR 结果", fontWeight = FontWeight.SemiBold)
            Text("时间", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Text(capturedAt, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("识别文字", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Text(
                state.lines.joinToString("\n").ifEmpty { "未识别到文字" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccessibilitySampleCard(sample: AccessibilitySample) {
    val capturedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        .format(Date(sample.capturedAtMillis))

    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("已捕获的微信界面样本", fontWeight = FontWeight.SemiBold)
            Text("时间", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Text(capturedAt, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("窗口", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Text(sample.windowClass ?: "未知", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("可见文字", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Text(
                sample.lines.joinToString("\n"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationSampleCard(sample: NotificationSample) {
    val fields = listOf(
        "时间" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(sample.postedAtMillis)),
        "title" to sample.title,
        "text" to sample.text,
        "bigText" to sample.bigText,
        "subText" to sample.subText,
        "textLines" to sample.textLines.joinToString("\n").ifEmpty { null },
    )

    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("已捕获的内存样本", fontWeight = FontWeight.SemiBold)
            fields.forEach { (label, value) ->
                Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                Text(value ?: "空", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: PreviewTransaction) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = if (transaction.pending) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        transaction.category,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (transaction.pending) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    transaction.amount,
                    color = if (transaction.income) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(transaction.objectName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                transaction.sourceAndTime,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun CrowBrandMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.crow_brand),
        contentDescription = "乌鸦标志",
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(18.dp)),
    )
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        CrowBrandMark(Modifier.size(44.dp))
    }
}

@Composable
private fun PlaceholderPage(title: String, description: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AutoLedgerPreview() {
    AutoLedgerTheme {
        AutoLedgerApp()
    }
}
