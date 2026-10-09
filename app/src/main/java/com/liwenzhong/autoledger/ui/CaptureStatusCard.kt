package com.liwenzhong.autoledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liwenzhong.autoledger.capture.CaptureDiagnostics
import com.liwenzhong.autoledger.capture.CaptureEvent
import com.liwenzhong.autoledger.capture.CaptureSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun CaptureStatusCard() {
    val state by CaptureDiagnostics.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
    )) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("最近处理", style = MaterialTheme.typography.titleMedium)
            CaptureSource.entries.forEach { source ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(source.label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.latest[source]?.let { "${it.outcome.label} · ${eventTime(it)}" }
                        ?: "本次启动尚无记录", fontWeight = FontWeight.Medium)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("最近保存：${state.lastSaved?.let(::eventTime) ?: "本次启动尚未保存"}", fontSize = 13.sp)
            state.lastFailure?.let {
                Text("最近失败：${it.source.label} · ${it.outcome.label} · ${eventTime(it)}",
                    color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Text("连接成功不代表入账成功。付款码通常不是交易结果；保存后请到待确认核对。",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起处理记录" else "查看处理记录") }
            if (expanded) {
                state.recent.forEach { event ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(event.outcome.label, fontSize = 13.sp)
                            Text(event.source.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(eventTime(event), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("仅保留本次进程的最近 12 条结果及时间，不记录原文、金额或图片。重启后清空。",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun eventTime(event: CaptureEvent): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(event.atMillis))
