package com.example.englishcantoneselearning.ui.settings

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.ui.EditorialCard
import com.example.englishcantoneselearning.ui.MetadataPill
import com.example.englishcantoneselearning.ui.theme.EditorialTerracotta
import com.example.englishcantoneselearning.ui.material.ConnectionState
import kotlin.math.abs

@Composable
internal fun ProviderCard(
    provider: MaterialProviderConfig,
    index: Int,
    count: Int,
    connectionState: ConnectionState,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onTest: () -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var drag by remember(provider.id, index) { mutableFloatStateOf(0f) }
    EditorialCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("${index + 1}. ${provider.name}", style = MaterialTheme.typography.titleMedium)
                    Text(provider.model, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = provider.enabled, onCheckedChange = onEnabled)
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "拖动排序",
                    modifier = Modifier.pointerInput(provider.id, index) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = { drag = 0f },
                            onDragCancel = { drag = 0f },
                        ) { change, amount ->
                            change.consume()
                            drag += amount.y
                            if (abs(drag) >= 48f) {
                                val target = if (drag > 0) index + 1 else index - 1
                                if (target in 0 until count) onMove(index, target)
                                drag = 0f
                            }
                        }
                    },
                )
            }
            Text(provider.baseUrl, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CredentialStatus(provider.apiKey.isNotBlank(), "API Key")
                MetadataPill(
                    text = when (connectionState) {
                        ConnectionState.IDLE -> if (provider.enabled) "等待测试" else "已停用"
                        ConnectionState.CHECKING -> "测试中…"
                        ConnectionState.READY -> "连接正常"
                        ConnectionState.ERROR -> "连接失败"
                    },
                    accent = connectionState == ConnectionState.READY,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onTest, enabled = connectionState != ConnectionState.CHECKING) {
                    Text(if (connectionState == ConnectionState.CHECKING) "测试中…" else "测试")
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = EditorialTerracotta)
                }
            }
        }
    }
}

@Composable
internal fun ProviderEditor(
    original: MaterialProviderConfig,
    onDismiss: () -> Unit,
    onSave: (MaterialProviderConfig) -> Unit,
) {
    var name by remember(original.id) { mutableStateOf(original.name) }
    var url by remember(original.id) { mutableStateOf(original.baseUrl) }
    var model by remember(original.id) { mutableStateOf(original.model) }
    var key by remember(original.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (original.name.isBlank()) "添加材料模型" else "编辑${original.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Base URL") }, singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text("模型 ID") }, singleLine = true)
                OutlinedTextField(
                    key, { key = it },
                    label = { Text(if (original.apiKey.isBlank()) "API Key" else "输入新Key以替换") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(original.copy(name = name, baseUrl = url, model = model, apiKey = key.trim().ifBlank { original.apiKey }))
                },
                enabled = name.isNotBlank() && url.isNotBlank() && model.isNotBlank() &&
                    (key.isNotBlank() || original.apiKey.isNotBlank()),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
