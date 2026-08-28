package com.example.englishcantoneselearning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.englishcantoneselearning.model.SentenceItem

@Composable
internal fun EditSentenceDialog(
    sentence: SentenceItem,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onSplit: (String, Int) -> Unit,
) {
    var value by remember(sentence.id) {
        mutableStateOf(
            TextFieldValue(
                text = sentence.text,
                selection = TextRange(sentence.text.length),
            ),
        )
    }
    val splitPosition = value.selection.start
    val canSplit = value.selection.collapsed &&
        splitPosition in 1 until value.text.length &&
        value.text.substring(0, splitPosition).isNotBlank() &&
        value.text.substring(splitPosition).isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("编辑句子") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sentence_editor"),
                    minLines = 3,
                )
                Text(
                    text = "要拆分句子，请把光标放到拆分位置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { onSplit(value.text, splitPosition) },
                    enabled = canSplit,
                    modifier = Modifier.testTag("split_sentence_button"),
                ) {
                    Text("从光标拆分")
                }
                TextButton(
                    onClick = { onSave(value.text) },
                    enabled = value.text.isNotBlank(),
                    modifier = Modifier.testTag("save_sentence_button"),
                ) {
                    Text("保存")
                }
            }
        },
    )
}
