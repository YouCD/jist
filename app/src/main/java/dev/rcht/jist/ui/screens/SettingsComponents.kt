package dev.rcht.jist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SummarizationStyleDialog(
    currentTone: String,
    currentLength: String,
    onSelectTone: (String) -> Unit,
    onSelectLength: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedTone by remember { mutableStateOf(currentTone) }
    var selectedLength by remember { mutableStateOf(currentLength.ifBlank { "MEDIUM" }) }

    val isZh = java.util.Locale.getDefault().language == "zh"

    val tones = listOf(
        "PROFESSIONAL" to if (isZh) "专业" else "Professional",
        "CASUAL" to if (isZh) "随意" else "Casual",
        "WITTY" to if (isZh) "幽默" else "Witty",
        "URGENT" to if (isZh) "紧急" else "Urgent"
    )
    val builder = remember { dev.rcht.jist.llm.PromptBuilder() }
    val toneDesc = mapOf(
        "PROFESSIONAL" to if (isZh) "正式、中立" else "Formal & neutral",
        "CASUAL" to if (isZh) "口语化、轻松" else "Conversational",
        "WITTY" to if (isZh) "轻松幽默" else "Light-hearted",
        "URGENT" to if (isZh) "突出紧急事项" else "Action-oriented"
    )
    val lengths = listOf(
        "SHORT" to if (isZh) "短" else "Short",
        "MEDIUM" to if (isZh) "中" else "Medium",
        "LONG" to if (isZh) "长" else "Long"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isZh) "摘要风格" else "Summarization Style") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isZh) "语气" else "Tone",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                tones.forEach { (value, label) ->
                    val isSelected = selectedTone == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTone = value }
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { selectedTone = value })
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = builder.toneInstruction(value),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isSelected) 0.8f else 0.5f)
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = if (isZh) "长度" else "Length",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    lengths.forEach { (value, label) ->
                        val sub = when (value) {
                            "SHORT" -> if (isZh) "50字以内" else "50 words"
                            "MEDIUM" -> if (isZh) "150字以内" else "150 words"
                            "LONG" -> if (isZh) "300字以内" else "300 words"
                            else -> ""
                        }
                        FilterChip(
                            selected = selectedLength == value,
                            onClick = { selectedLength = value },
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = label, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(text = sub, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSelectTone(selectedTone)
                onSelectLength(selectedLength)
                onDismiss()
            }) {
                Text(if (isZh) "保存" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isZh) "取消" else "Cancel")
            }
        }
    )
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun McpPortDialog(
    currentPort: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentPort.toString()) }
    val parsed = text.trim().toIntOrNull()
    val valid = parsed != null && parsed in 1024..65535
    val isZh = java.util.Locale.getDefault().language == "zh"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isZh) "修改端口" else "Change Port") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { newValue ->
                        if (newValue.length <= 5 && newValue.all { c -> c.isDigit() }) {
                            text = newValue
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    label = { Text(if (isZh) "端口（1024-65535）" else "Port (1024-65535)") },
                    isError = text.isNotBlank() && !valid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (text.isNotBlank() && !valid) {
                    Text(
                        text = if (isZh) "请输入 1024-65535 之间的端口号" else "Enter a port between 1024 and 65535",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onConfirm(it) } },
                enabled = valid
            ) {
                Text(if (isZh) "保存" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isZh) "取消" else "Cancel")
            }
        }
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector? = null,
    title: String,
    value: String? = null,
    showChevron: Boolean = true,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
             Spacer(modifier = Modifier.width(16.dp))
        }
       
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        
        if (trailingIcon != null) {
             Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
         Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
