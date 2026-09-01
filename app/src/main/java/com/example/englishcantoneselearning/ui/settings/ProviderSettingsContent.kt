package com.example.englishcantoneselearning.ui.settings

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.model.MaterialProviderConfig
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
    var confirmDelete by remember(provider.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_drag_handle),
                contentDescription = "长按拖动 ${provider.name} 调整顺序",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp).pointerInput(provider.id, index) {
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    provider.name.ifBlank { "未命名模型" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    provider.model,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = provider.enabled, onCheckedChange = onEnabled)
        }

        Text(
            provider.baseUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderStatus(connectionState, provider.enabled, provider.apiKey.isNotBlank())
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onTest,
                enabled = connectionState != ConnectionState.CHECKING,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                if (connectionState == ConnectionState.CHECKING) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(6.dp))
                    Text("测试中")
                } else {
                    Text("测试")
                }
            }
            IconButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = "编辑 ${provider.name}")
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = "删除 ${provider.name}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("删除材料模型？") },
            text = { Text("将删除“${provider.name}”及其本机配置，此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ProviderStatus(state: ConnectionState, enabled: Boolean, configured: Boolean) {
    val label = when (state) {
        ConnectionState.IDLE -> if (enabled) "等待测试" else "已停用"
        ConnectionState.CHECKING -> "测试中"
        ConnectionState.READY -> "连接正常"
        ConnectionState.ERROR -> "连接失败"
    }
    val color = when (state) {
        ConnectionState.READY -> MaterialTheme.colorScheme.primary
        ConnectionState.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    Text("·", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
    CredentialStatus(configured = configured, label = "API Key")
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
        shape = RoundedCornerShape(20.dp),
        title = { Text(if (original.name.isBlank()) "添加材料模型" else "编辑材料模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "仅支持 OpenAI Responses 兼容接口。已保存的密钥不会回显。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型 ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(if (original.apiKey.isBlank()) "API Key" else "输入新 Key 以替换") },
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = { if (original.apiKey.isNotBlank()) Text("已保存：••••••••") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        original.copy(
                            name = name.trim(),
                            baseUrl = url.trim(),
                            model = model.trim(),
                            apiKey = key.trim().ifBlank { original.apiKey },
                        ),
                    )
                },
                enabled = name.isNotBlank() && url.isNotBlank() && model.isNotBlank() &&
                    (key.isNotBlank() || original.apiKey.isNotBlank()),
                modifier = Modifier.testTag("save_provider_button"),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
