package com.liwenzhong.autoledger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.liwenzhong.autoledger.parser.WECHAT_SCREEN_SOURCE
import com.liwenzhong.autoledger.parser.WeChatNotificationParser
import java.text.SimpleDateFormat
import java.util.Calendar
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

private data class TransactionDayGroup(
    val key: String,
    val label: String,
    val expenseCents: Long,
    val transactions: List<PreviewTransaction>,
)

private data class DailyExpenseTotal(
    val label: String,
    val amountCents: Long,
)

internal data class CategoryExpenseTotal(
    val category: String,
    val amountCents: Long,
)

private enum class ChartRange(val label: String) {
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
}

private val ManualCategories = listOf(
    "餐饮", "出行", "服饰", "日用",
    "娱乐", "医疗", "学习", "住房",
    "人情", "收入", "转账", "其他",
)

@Composable
fun AutoLedgerApp(
    notificationAccessGranted: Boolean = false,
    notificationListenerConnected: Boolean = false,
    notificationRecoveryMessage: String? = null,
    accessibilityAccessGranted: Boolean = false,
    accessibilityAutoCaptureEnabled: Boolean = false,
    accessibilityServiceRunning: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
    onReconnectNotifications: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
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
    val tabs = listOf("账目", "统计", "采集", "记一笔")
    BackHandler(enabled = showSettings || showPending || selectedTab != 0) {
        showSettings = false
        showPending = false
        selectedTab = 0
    }

    Scaffold(
        bottomBar = {
            if (!showSettings && !showPending) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                LedgerIcon(
                                    symbol = listOf(LedgerSymbol.BOOK, LedgerSymbol.CHART,
                                        LedgerSymbol.CAPTURE, LedgerSymbol.PLUS)[index],
                                    tint = if (selectedTab == index) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
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
            (showSettings || selectedTab == 2) -> NotificationCapturePage(
                    notificationAccessGranted = notificationAccessGranted,
                    notificationListenerConnected = notificationListenerConnected,
                    notificationRecoveryMessage = notificationRecoveryMessage,
                    accessibilityAccessGranted = accessibilityAccessGranted,
                    accessibilityAutoCaptureEnabled = accessibilityAutoCaptureEnabled,
                    accessibilityServiceRunning = accessibilityServiceRunning,
                    onBack = { showSettings = false; selectedTab = 0 },
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onReconnectNotifications = onReconnectNotifications,
                    onOpenAppSettings = onOpenAppSettings,
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
                    notificationAccessGranted = notificationAccessGranted,
                    notificationListenerConnected = notificationListenerConnected,
                    accessibilityAccessGranted = accessibilityAccessGranted,
                    accessibilityServiceRunning = accessibilityServiceRunning,
                    accessibilityAutoCaptureEnabled = accessibilityAutoCaptureEnabled,
                    onPendingClick = { showPending = true },
                    onSettingsClick = { showSettings = true },
                    modifier = Modifier.padding(padding),
                )
                1 -> LedgerCharts(
                    transactions = pending + confirmed,
                    modifier = Modifier.padding(padding),
                )
                else -> ManualLedgerPreview(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun LedgerHome(
    pending: List<TransactionCandidate>,
    confirmed: List<TransactionCandidate>,
    notificationAccessGranted: Boolean,
    notificationListenerConnected: Boolean,
    accessibilityAccessGranted: Boolean,
    accessibilityServiceRunning: Boolean,
    accessibilityAutoCaptureEnabled: Boolean,
    onPendingClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pendingFingerprints = pending.mapTo(mutableSetOf(), TransactionCandidate::fingerprint)
    val transactions = (pending + confirmed)
        .filter { isInCurrentMonth(it.occurredAtMillis) }
        .sortedByDescending(TransactionCandidate::occurredAtMillis)
    val expenseCents = transactions.filter { it.direction == TransactionDirection.EXPENSE }.sumOf { it.amountCents ?: 0L }
    val incomeCents = transactions.filter { it.direction == TransactionDirection.INCOME }.sumOf { it.amountCents ?: 0L }
    val dayGroups = transactionDayGroups(transactions, pendingFingerprints)
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CrowBrandMark(Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text("我的账本", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(formatCurrentMonth(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("本月已支出", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp)
                    Text(formatYuan(expenseCents), fontSize = 32.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("收入 ${formatYuan(incomeCents)}", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${transactions.size} 笔账目", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    if (transactions.any { it.fingerprint in pendingFingerprints }) {
                        Text("含待确认金额", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                }
            }
        }
        item {
            Surface(onClick = onPendingClick, shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LedgerIcon(LedgerSymbol.CHECK, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(if (pending.isEmpty()) "暂无待确认账目" else "${pending.size} 笔账目待确认",
                        modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    LedgerIcon(LedgerSymbol.ARROW)
                }
            }
        }
        item {
            Surface(onClick = onSettingsClick, color = Color.Transparent, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("通知 · ${notificationConnectionLabel(notificationAccessGranted, notificationListenerConnected)}" +
                        "    界面 · ${accessibilityCaptureLabel(accessibilityAccessGranted, accessibilityAutoCaptureEnabled, accessibilityServiceRunning)}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("查看采集与处理结果 →", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Text("本月明细", style = MaterialTheme.typography.titleMedium)
        }
        if (dayGroups.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LedgerIcon(LedgerSymbol.BOOK, modifier = Modifier.size(36.dp))
                    Text("从第一笔开销开始", fontWeight = FontWeight.SemiBold)
                    Text("识别成功的交易将在这里出现", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onSettingsClick) { Text("检查自动采集") }
                }
            }
        } else {
            dayGroups.forEach { group -> item(key = "day-${group.key}") { DayTransactionCard(group) } }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun DayTransactionCard(group: TransactionDayGroup) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
    )) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(group.label, fontWeight = FontWeight.Bold)
                Text(
                    "支出 ${formatYuan(group.expenseCents)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            group.transactions.forEachIndexed { index, transaction ->
                CompactTransactionRow(transaction)
                if (index != group.transactions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 48.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactTransactionRow(transaction: PreviewTransaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = if (transaction.pending) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                LedgerIcon(LedgerSymbol.CATEGORY, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.objectName, fontWeight = FontWeight.SemiBold)
            Text(
                "${transaction.category} · ${transaction.sourceAndTime}${if (transaction.pending) " · 待确认" else ""}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        Text(
            transaction.amount,
            color = if (transaction.income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LedgerCharts(
    transactions: List<TransactionCandidate>,
    modifier: Modifier = Modifier,
) {
    var selectedRange by remember { mutableStateOf(ChartRange.MONTH) }
    val visible = transactionsInRange(transactions, selectedRange)
    val expenseTotal = visible
        .filter { it.direction == TransactionDirection.EXPENSE }
        .sumOf { it.amountCents ?: 0L }
    val dailyTotals = dailyExpenseTotals(visible)
    val categoryTotals = expenseTotalsByCategory(visible)
    val average = if (dailyTotals.isEmpty()) 0L else expenseTotal / dailyTotals.size

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CrowBrandMark(Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("支出统计", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "本地账目 · 含待确认金额",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                ) {
                    ChartRange.entries.forEach { range ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedRange = range },
                            color = if (selectedRange == range) {
                                CrowInk
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(
                                range.label,
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = if (selectedRange == range) CrowIvory else MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(26.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("总支出", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(formatYuan(expenseTotal), color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("有支出日均  ${formatYuan(average)}", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("支出趋势", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (dailyTotals.isEmpty()) {
                        Text("当前范围暂无支出", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("最近 7 个有支出的日期", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ExpenseBars(dailyTotals.takeLast(7))
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("分类排行", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (categoryTotals.isEmpty()) {
                        Text("当前范围暂无分类数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val maximum = categoryTotals.first().amountCents.coerceAtLeast(1L)
                        categoryTotals.take(6).forEach { total ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(total.category, fontWeight = FontWeight.Medium)
                                    Text(formatYuan(total.amountCents), fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHighest,
                                            RoundedCornerShape(50),
                                        ),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(total.amountCents.toFloat() / maximum)
                                            .height(7.dp)
                                            .background(CrowTeal, RoundedCornerShape(50)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun ExpenseBars(totals: List<DailyExpenseTotal>) {
    val maximum = totals.maxOf(DailyExpenseTotal::amountCents).coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        totals.forEach { total ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((12 + 88 * total.amountCents.toFloat() / maximum).dp)
                        .background(CrowTeal, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                )
                Spacer(Modifier.height(6.dp))
                Text(total.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ManualLedgerPreview(modifier: Modifier = Modifier) {
    var expense by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf("餐饮") }
    var amount by remember { mutableStateOf("0") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("记一笔", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "先预览录入方式，暂不保存到账本",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                ) {
                    listOf(true to "支出", false to "收入").forEach { (isExpense, label) ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { expense = isExpense },
                            color = if (expense == isExpense) CrowInk else Color.Transparent,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = if (expense == isExpense) CrowIvory else MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ManualCategories.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { category ->
                            val selected = selectedCategory == category
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedCategory = category },
                                color = if (selected) CrowAmber else MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(category.take(1), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(category, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(selectedCategory, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        Text(
                            if (expense) "支出金额" else "收入金额",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        "¥$amount",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf(
                    listOf("7", "8", "9"),
                    listOf("4", "5", "6"),
                    listOf("1", "2", "3"),
                    listOf(".", "0", "⌫"),
                ).forEach { keys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        keys.forEach { key ->
                            Button(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                onClick = { amount = updateManualAmount(amount, key) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {},
                enabled = false,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("保存功能下一步接入")
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
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
            IconButton(onClick = onBack) { LedgerIcon(LedgerSymbol.BACK, description = "返回") }
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
        if (candidate.source == WECHAT_SCREEN_SOURCE) {
            item {
                Text(
                    "这笔来自页面识别，请核对实际交易时间及是否重复。相同页面只在 90 秒内合并；短时间同内容的新交易可能漏记，久后重看的旧账单可能再次出现。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
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

private fun transactionDayGroups(
    transactions: List<TransactionCandidate>,
    pendingFingerprints: Set<String>,
): List<TransactionDayGroup> = transactions
    .groupBy {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.occurredAtMillis))
    }
    .map { (key, candidates) ->
        TransactionDayGroup(
            key = key,
            label = SimpleDateFormat("M月d日 EEEE", Locale.getDefault())
                .format(Date(candidates.first().occurredAtMillis)),
            expenseCents = candidates
                .filter { it.direction == TransactionDirection.EXPENSE }
                .sumOf { it.amountCents ?: 0L },
            transactions = candidates.map { it.toPreview(it.fingerprint in pendingFingerprints) },
        )
    }

private fun isInCurrentMonth(occurredAtMillis: Long): Boolean {
    val now = Calendar.getInstance()
    val occurred = Calendar.getInstance().apply { timeInMillis = occurredAtMillis }
    return now.get(Calendar.YEAR) == occurred.get(Calendar.YEAR) &&
        now.get(Calendar.MONTH) == occurred.get(Calendar.MONTH)
}

private fun transactionsInRange(
    transactions: List<TransactionCandidate>,
    range: ChartRange,
    nowMillis: Long = System.currentTimeMillis(),
): List<TransactionCandidate> {
    val start = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        when (range) {
            ChartRange.WEEK -> add(Calendar.DAY_OF_MONTH, -6)
            ChartRange.MONTH -> set(Calendar.DAY_OF_MONTH, 1)
            ChartRange.YEAR -> {
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }.timeInMillis
    return transactions.filter { it.occurredAtMillis in start..nowMillis }
}

private fun dailyExpenseTotals(transactions: List<TransactionCandidate>): List<DailyExpenseTotal> =
    transactions
        .filter { it.direction == TransactionDirection.EXPENSE && (it.amountCents ?: 0L) > 0L }
        .groupBy {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.occurredAtMillis))
        }
        .entries
        .sortedBy { it.value.first().occurredAtMillis }
        .map { (_, candidates) ->
            DailyExpenseTotal(
                label = SimpleDateFormat("M/d", Locale.getDefault())
                    .format(Date(candidates.first().occurredAtMillis)),
                amountCents = candidates.sumOf { it.amountCents ?: 0L },
            )
        }

internal fun expenseTotalsByCategory(
    transactions: List<TransactionCandidate>,
): List<CategoryExpenseTotal> = transactions
    .filter { it.direction == TransactionDirection.EXPENSE && (it.amountCents ?: 0L) > 0L }
    .groupBy { transactionCategoryLabel(it.category, it.direction) }
    .map { (category, candidates) ->
        CategoryExpenseTotal(category, candidates.sumOf { it.amountCents ?: 0L })
    }
    .sortedByDescending(CategoryExpenseTotal::amountCents)

internal fun updateManualAmount(current: String, key: String): String = when {
    key == "⌫" -> current.dropLast(1).ifEmpty { "0" }
    key == "." -> if ('.' in current) current else "$current."
    key.length != 1 || !key[0].isDigit() -> current
    current.substringAfter('.', "").length >= 2 -> current
    current.count(Char::isDigit) >= 9 -> current
    current == "0" -> key
    else -> current + key
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

internal fun notificationConnectionLabel(granted: Boolean, connected: Boolean): String = when {
    !granted -> "未授权"
    !connected -> "未连接"
    else -> "已连接"
}

internal fun accessibilityCaptureLabel(granted: Boolean, enabled: Boolean, running: Boolean): String = when {
    !granted -> "未授权"
    !enabled -> "未开启"
    !running -> "未连接"
    else -> "运行中"
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
    notificationListenerConnected: Boolean,
    notificationRecoveryMessage: String?,
    accessibilityAccessGranted: Boolean,
    accessibilityAutoCaptureEnabled: Boolean,
    accessibilityServiceRunning: Boolean,
    onBack: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onReconnectNotifications: () -> Unit,
    onOpenAppSettings: () -> Unit,
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
            PageHeader("自动采集", onBack)
        }
        item { CaptureStatusCard() }

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
                        notificationConnectionLabel(notificationAccessGranted, notificationListenerConnected),
                        color = if (notificationAccessGranted && notificationListenerConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    if (notificationAccessGranted) {
                        Text(
                            if (notificationListenerConnected) {
                                "通知服务已连接，微信通知会在后台自动解析，无需停留在记账页面。"
                            } else {
                                "已授权，但通知服务未连接，现在可能漏记。可以请求重连；仍未连接时，请在系统设置中重新开关通知访问。"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!notificationListenerConnected) {
                            Button(onClick = onReconnectNotifications) { Text("重新连接通知") }
                        }
                        notificationRecoveryMessage?.let { message ->
                            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
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

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("后台运行", fontWeight = FontWeight.SemiBold)
                    Text(
                        "无需一直打开记账页面。在应用信息或电池设置中，检查“允许后台活动”和“自启动”。系统停止应用后需要重新打开；无障碍授权关闭后，需要你手动重新开启。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "微信通知必须提供交易信息才能解析；界面采集需要微信在前台。中断期间已消失的通知和页面无法自动补回。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    OutlinedButton(onClick = onOpenAppSettings) { Text("打开应用信息") }
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
                                    if (notificationListenerConnected) {
                                        "正在等待下一条微信通知；后台自动解析仍然有效。"
                                    } else {
                                        "已准备查看样本，但通知服务未连接，请先恢复连接。"
                                    }
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
                            !accessibilityAutoCaptureEnabled -> "已授权，但应用内开关未开启，自动采集已暂停。"
                            !accessibilityServiceRunning -> "已授权，但服务当前未连接。请进入无障碍设置重新开关此服务。"
                            else -> "只读服务已连接，微信在前台时会自动采集。"
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
                    OutlinedButton(onClick = onOpenAccessibilitySettings) {
                        Text("打开无障碍设置")
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
                "当前支持微信。自动采集由微信前台事件触发单帧识别；手动截图每次需要系统授权。不会保存截图或操作微信。",
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
            IconButton(onClick = onBack) { LedgerIcon(LedgerSymbol.BACK, description = "返回") }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        CrowBrandMark(Modifier.size(28.dp))
    }
}

@Preview(name = "Light", showBackground = true, widthDp = 393, heightDp = 852)
@Preview(name = "Dark", showBackground = true, widthDp = 393, heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large text", showBackground = true, widthDp = 393, heightDp = 852, fontScale = 1.3f)
@Composable
private fun AutoLedgerPreview() {
    AutoLedgerTheme {
        AutoLedgerApp()
    }
}
