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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class PreviewTransaction(
    val category: String,
    val objectName: String,
    val amount: String,
    val sourceAndTime: String,
    val income: Boolean = false,
)

private val previewTransactions = listOf(
    PreviewTransaction("餐饮", "沙县小吃", "-¥18.00", "微信 · 今天 12:26"),
    PreviewTransaction("未分类", "未确定", "-¥36.80", "招商银行 · 今天 10:41"),
    PreviewTransaction("红包", "微信红包", "+¥20.00", "微信 · 昨天 21:08", income = true),
)

@Composable
fun AutoLedgerApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("账目", "图表", "记一笔")

    Scaffold(
        bottomBar = {
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
        },
    ) { padding ->
        when (selectedTab) {
            0 -> LedgerHome(Modifier.padding(padding))
            1 -> PlaceholderPage("图表", "接入真实账目后显示分类占比和收支趋势。", Modifier.padding(padding))
            else -> PlaceholderPage("手动记账", "自动识别不是重点时，也可以在这里补记一笔。", Modifier.padding(padding))
        }
    }
}

@Composable
private fun LedgerHome(modifier: Modifier = Modifier) {
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
                Text("设置", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
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
                        SummaryValue("支出", "¥1,286.50")
                        SummaryValue("收入", "¥3,000.00")
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            "待处理 2 笔",
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

        items(previewTransactions) { transaction ->
            TransactionRow(transaction)
        }

        item {
            Text(
                "当前为界面预览，尚未接入通知和真实账目。",
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
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
