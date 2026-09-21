package dev.rcht.jist.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rcht.jist.ui.components.GlassCard
import dev.rcht.jist.ui.xposedchats.XposedChatItem
import dev.rcht.jist.util.displayContent
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun XposedChatCard(item: XposedChatItem, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onToggleSummarized: (Boolean) -> Unit, onSaveChatSettings: (customPrompt: String?, minMessages: Int, retentionDays: Int) -> Unit, onChatSummarize: () -> Unit, snackbarHostState: SnackbarHostState? = null) {
    val defaultPrompt by rememberUpdatedState(dev.rcht.jist.llm.getDefaultSystemPrompt())
    var expanded by remember { mutableStateOf(false) }
    var promptText by remember { mutableStateOf(item.chat.customPrompt ?: defaultPrompt) }
    var minMessagesText by remember { mutableStateOf(item.chat.minMessagesForSummary.toString()) }
    var retentionDaysText by remember { mutableStateOf(item.chat.retentionDays.toString()) }
    var savedPrompt by remember { mutableStateOf(item.chat.customPrompt ?: defaultPrompt) }
    var savedMinMessages by remember { mutableStateOf(item.chat.minMessagesForSummary.toString()) }
    var savedRetentionDays by remember { mutableStateOf(item.chat.retentionDays.toString()) }
    val hasChanges by derivedStateOf { promptText != savedPrompt || minMessagesText != savedMinMessages || retentionDaysText != savedRetentionDays }
    val isDefault by derivedStateOf { promptText == defaultPrompt }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val summaryActionWidth = 72.dp
    val summaryActionGap = 12.dp
    val summaryActionWidthPx by derivedStateOf { with(density) { summaryActionWidth.toPx() } }
    val summaryActionTotalPx by derivedStateOf { with(density) { (summaryActionWidth + summaryActionGap).toPx() } }
    val offsetX = remember { Animatable(0f) }
    val cardOpen by derivedStateOf { offsetX.value < -0.5f }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .height(45.dp)
                .width(summaryActionWidth)
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable {
                    scope.launch { offsetX.animateTo(0f, tween(200)) }
                    onChatSummarize()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = "AI摘要", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = if (offsetX.value < -summaryActionWidthPx / 2f) -summaryActionTotalPx else 0f,
                                    animationSpec = tween(200)
                                )
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-summaryActionTotalPx, 0f))
                            }
                        }
                    )
                }
        ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                .combinedClickable(
                    onClick = {
                        if (offsetX.value < -0.5f) {
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = { if (!selecting) onLongClick() }
                ),
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(12.dp)
        ) {
        Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = if (expanded) 0.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = item.chat.chatName, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        if (item.latestMessage != null) {
                            Text(text = formatTimestamp(item.latestMessage.timestamp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val msg = item.latestMessage
                        if (msg != null) {
                            Text(
                                text = buildString {
                                    if (msg.senderName.isNotBlank()) {
                                        append(msg.senderName); append(": ")
                                    }
                                    append(displayContent(msg.content))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (item.messageCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(text = item.messageCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
                if (!selecting && item.chat.isSummarized) {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.Edit,
                            contentDescription = if (expanded) "收起" else "编辑",
                            tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!selecting) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(checked = item.chat.isSummarized, onCheckedChange = onToggleSummarized, modifier = Modifier.height(24.dp))
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    Text("自定义摘要提示词", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
                    if (isDefault) {
                        Text("正在使用默认提示词 — 编辑下方内容以自定义：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 4.dp))
                    }
                    OutlinedTextField(
                        value = promptText, onValueChange = { promptText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("自定义提示词") }, minLines = 2, maxLines = 6,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("摘要前最少消息数", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    OutlinedTextField(
                        value = minMessagesText,
                        onValueChange = { minMessagesText = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("5") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("消息保留天数（0=永久）", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    OutlinedTextField(
                        value = retentionDaysText,
                        onValueChange = { retentionDaysText = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("7") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        if (!isDefault) {
                            TextButton(onClick = {
                                promptText = defaultPrompt
                                onSaveChatSettings(defaultPrompt, item.chat.minMessagesForSummary, item.chat.retentionDays)
                                focusManager.clearFocus()
                            }) { Text("重置为默认", color = MaterialTheme.colorScheme.error) }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Button(
                            onClick = {
                                val count = minMessagesText.toIntOrNull() ?: 5
                                val days = retentionDaysText.toIntOrNull() ?: 0
                                if (count > 0) {
                                    onSaveChatSettings(promptText, count, days)
                                }
                                savedPrompt = promptText
                                savedMinMessages = minMessagesText
                                savedRetentionDays = retentionDaysText
                                scope.launch { snackbarHostState?.showSnackbar("已保存") }
                                focusManager.clearFocus()
                            },
                            enabled = hasChanges,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }
        }
    }
}
