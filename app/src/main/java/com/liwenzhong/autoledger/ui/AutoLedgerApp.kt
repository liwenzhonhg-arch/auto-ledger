package com.liwenzhong.autoledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
)

@Composable
fun AutoLedgerApp(
    notificationAccessGranted: Boolean = false,
    accessibilityAccessGranted: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    onOpenWeChat: () -> Unit = {},
    onStartOcrCapture: () -> Unit = {},
    isDebugBuild: Boolean = false,
    onSimulateNotification: () -> Unit = {},
    pendingTransactions: Flow<List<TransactionCandidate>> = flowOf(emptyList()),
    confirmedTransactions: Flow<List<TransactionCandidate>> = flowOf(emptyList()),
    onConfirmCandidate: suspend (String, Long?, String, String) -> Boolean =
        { _, _, _, _ -> false },
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
                    onBack = { showSettings = false },
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenWeChat = onOpenWeChat,
                    onStartOcrCapture = onStartOcrCapture,
                    isDebugBuild = isDebugBuild,
                    onSimulateNotification = onSimulateNotification,
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
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Auto Ledger", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("离线自动记账", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onSettingsClick) {
                    Text("设置", fontWeight = FontWeight.Medium)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("2026 年 7 月", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SummaryValue("支出", formatYuan(expenseCents))
                        SummaryValue("收入", formatYuan(incomeCents))
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            "待处理 ${pending.size} 笔",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        item {
            Text("最近账目", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }

        if (transactions.isNotEmpty()) {
            items(transactions) { candidate ->
                TransactionRow(candidate.toPreview(isPending = pending.any { it.fingerprint == candidate.fingerprint }))
            }
        } else {
            item {
                Text("暂无模拟账目，请先到设置生成一条。")
            }
        }

        if (pending.isNotEmpty()) {
            item {
                Button(onClick = onPendingClick) {
                    Text("处理待确认")
                }
            }
        }

        item {
            Text(
                "账目仅保存在本机应用内部，不会云同步。",
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("返回") }
                Text("确认账目", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Text(
                if (candidate.direction == TransactionDirection.INCOME) "收入候选" else "支出候选",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        category = category,
        objectName = counterparty,
        amount = amountText,
        sourceAndTime = "$source · ${formatTransactionTime(occurredAtMillis)} · ${
            if (isPending) "待确认" else "已确认"
        }",
        income = direction == TransactionDirection.INCOME,
    )
}

private fun formatYuan(cents: Long): String =
    "¥${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

private fun formatYuanInput(cents: Long): String =
    "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

private fun formatTransactionTime(occurredAtMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(occurredAtMillis))

@Composable
private fun NotificationCapturePage(
    notificationAccessGranted: Boolean,
    accessibilityAccessGranted: Boolean,
    onBack: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenWeChat: () -> Unit,
    onStartOcrCapture: () -> Unit,
    isDebugBuild: Boolean,
    onSimulateNotification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notificationArmed = NotificationSampleStore.armed
    val notificationSample = NotificationSampleStore.latest
    val accessibilityArmed = AccessibilitySampleStore.armed
    val accessibilitySample = AccessibilitySampleStore.latest
    val ocrState = OcrCaptureStore.state

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("返回") }
                Text("通知采集", fontSize = 25.sp, fontWeight = FontWeight.Bold)
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
                    Text("通知访问", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (notificationAccessGranted) "已允许" else "未允许",
                        color = if (notificationAccessGranted) Color(0xFF16803C) else MaterialTheme.colorScheme.error,
                    )
                    if (!notificationAccessGranted) {
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
                            Text("采集微信样本", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (notificationArmed) {
                                    "正在等待下一条微信通知。采集完成后会自动停止。"
                                } else {
                                    "只读取下一条微信通知；原始通知不写入数据库。"
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
                        color = if (accessibilityAccessGranted) Color(0xFF16803C) else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "仅在你主动开始采集后读取微信可见文字，不会点击、输入或滚动。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
private fun SummaryValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TransactionRow(transaction: PreviewTransaction) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(transaction.category, fontWeight = FontWeight.SemiBold)
                Text(
                    transaction.amount,
                    color = if (transaction.income) Color(0xFF16803C) else MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Spacer(Modifier.height(8.dp))
            Text(transaction.objectName)
            Text(
                transaction.sourceAndTime,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
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
