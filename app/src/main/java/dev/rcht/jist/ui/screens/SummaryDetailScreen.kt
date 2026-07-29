package dev.rcht.jist.ui.screens

import android.content.Intent
import dev.rcht.jist.R
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import dev.rcht.jist.ui.components.GlassScaffold
import dev.rcht.jist.ui.components.MarkdownText
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rcht.jist.ui.summarydetail.SummaryDetailUiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryDetailScreen(
    uiState: SummaryDetailUiState = SummaryDetailUiState(),
    onNavigateBack: () -> Unit = {},
    onReSummarize: () -> Unit = {},
    onPageChanged: (Int) -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    isReSummarizing: Boolean = false,
    modifier: Modifier = Modifier,
    formatDate: (Long) -> String = { timeMs ->
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timeMs))
    }
) {
    val context = LocalContext.current
    var targetIndex by remember { mutableIntStateOf(uiState.currentIndex) }
    val currentLazyListState = remember { mutableStateOf<LazyListState?>(null) }
    val currentSummaryScrollState = remember { mutableStateOf<androidx.compose.foundation.ScrollState?>(null) }
    val isChangingPage = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.currentIndex) {
        if (!isChangingPage.value) {
            targetIndex = uiState.currentIndex
        }
    }

    val overscrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y == 0f || isChangingPage.value || source != NestedScrollSource.UserInput)
                    return Offset.Zero

                val scrollingDown = available.y < 0f
                val summaryState = currentSummaryScrollState.value
                val listState = currentLazyListState.value

                val canScroll = if (scrollingDown)
                    (summaryState?.canScrollForward == true) || (listState?.canScrollForward == true)
                else
                    (summaryState?.canScrollBackward == true) || (listState?.canScrollBackward == true)

                // If any child can still scroll → let it handle the gesture
                if (canScroll) return Offset.Zero

                // Both at boundary → trigger page switch
                if (scrollingDown && targetIndex < uiState.summaries.size - 1) {
                    isChangingPage.value = true
                    coroutineScope.launch {
                        val next = targetIndex + 1
                        targetIndex = next
                        delay(300)
                        onPageChanged(next)
                        isChangingPage.value = false
                    }
                } else if (!scrollingDown && targetIndex > 0) {
                    isChangingPage.value = true
                    coroutineScope.launch {
                        val prev = targetIndex - 1
                        targetIndex = prev
                        delay(300)
                        onPageChanged(prev)
                        isChangingPage.value = false
                    }
                }
                return Offset.Zero
            }
        }
    }

    GlassScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val remaining = uiState.summaries.size - targetIndex - 1
                    Text(
                        if (uiState.summaries.isNotEmpty())
                            "${stringResource(R.string.summary_detail_title)} (${targetIndex + 1}/${uiState.summaries.size})"
                        else
                            stringResource(R.string.summary_detail_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    if (uiState.summaries.any { !it.isRead }) {
                        IconButton(onClick = onMarkAllRead) {
                            Icon(
                                Icons.Filled.DoneAll,
                                contentDescription = "全部标为已读",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(overscrollConnection)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else if (uiState.summaries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No summaries")
                }
            } else {
                AnimatedContent(
                    targetState = targetIndex,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInVertically { it * direction } togetherWith slideOutVertically { it * -direction })
                            .using(SizeTransform(clip = false))
                    },
                    label = "summary_pager"
                ) { page ->
                    val summary = uiState.summaries[page]
                    val notifications = if (page == uiState.currentIndex) uiState.notifications else emptyList()
                    val innerLazyListState = rememberLazyListState()

                    LaunchedEffect(page) {
                        currentLazyListState.value = innerLazyListState
                        innerLazyListState.scrollToItem(0)
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val maxSummaryCardHeight = maxHeight * 0.45f

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header card — swipe here triggers page change
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 8.dp)
                                    .pointerInput(targetIndex, uiState.summaries.size) {
                                        var total = 0f
                                        detectVerticalDragGestures(
                                            onDragStart = { total = 0f },
                                            onVerticalDrag = { _, dragAmount ->
                                                total += dragAmount
                                                if (total > 200f && targetIndex > 0) {
                                                    total = 0f
                                                    isChangingPage.value = true
                                                    coroutineScope.launch {
                                                        val prev = targetIndex - 1
                                                        targetIndex = prev
                                                        delay(300)
                                                        onPageChanged(prev)
                                                        isChangingPage.value = false
                                                    }
                                                } else if (total < -200f && targetIndex < uiState.summaries.size - 1) {
                                                    total = 0f
                                                    isChangingPage.value = true
                                                    coroutineScope.launch {
                                                        val next = targetIndex + 1
                                                        targetIndex = next
                                                        delay(300)
                                                        onPageChanged(next)
                                                        isChangingPage.value = false
                                                    }
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Card {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (!summary.isRead) {
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(end = 6.dp)
                                                            .width(8.dp)
                                                            .height(8.dp)
                                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = summary.appName,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = summary.contactOrGroup,
                                                        style = MaterialTheme.typography.titleSmall
                                                    )
                                                }
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "${summary.messageCount} msg",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                val remaining = uiState.summaries.size - targetIndex - 1
                                                if (remaining > 0) {
                                                    Text(
                                                        text = "剩余 $remaining",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = formatDate(summary.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Summary text section — scrollable within max 45% height
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.summary_label),
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 80.dp, max = maxSummaryCardHeight)
                                ) {
                                    var showGlow by remember(summary.summaryText) { mutableStateOf(false) }
                                    LaunchedEffect(summary.summaryText) {
                                        showGlow = true
                                        delay(80)
                                        showGlow = false
                                    }
                                    if (isReSummarizing && targetIndex == uiState.currentIndex) {
                                        val t = rememberInfiniteTransition()
                                        WaveText("正在生成…", t)
                                    } else {
                                        val summaryScrollState = rememberScrollState()
                                        var isScrolling by remember { mutableStateOf(false) }

                                        LaunchedEffect(page) {
                                            currentSummaryScrollState.value = summaryScrollState
                                        }

                                        LaunchedEffect(summaryScrollState.isScrollInProgress) {
                                            isScrolling = summaryScrollState.isScrollInProgress
                                        }

                                        Column(
                                            modifier = Modifier
                                                .verticalScroll(summaryScrollState)
                                                .fillMaxWidth()
                                        ) {
                                            val enhancedText = remember(summary.summaryText) {
                                                enhancePriorityLabels(summary.summaryText)
                                            }
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (showGlow) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                                                )
                                            ) {
                                                MarkdownText(
                                                    markdown = enhancedText,
                                                    modifier = Modifier.padding(16.dp)
                                                )
                                            }
                                        }

                                        // Thin scrollbar indicator
                                        if (isScrolling && summaryScrollState.maxValue > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .padding(vertical = 4.dp)
                                                    .height(40.dp)
                                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), shape = CircleShape)
                                                    .align(Alignment.TopEnd)
                                                    .padding(end = 3.dp)
                                            )
                                        }

                                        // Bottom gradient mask when content overflows
                                        if (summaryScrollState.maxValue > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(32.dp)
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color.Transparent,
                                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                                            )
                                                        )
                                                    )
                                                    .align(Alignment.BottomCenter)
                                            )
                                        }

                                        // Top indicator line when scrolled down
                                        if (summaryScrollState.value > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                                    .align(Alignment.TopCenter)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            if (notifications.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "${stringResource(R.string.summary_original_notifications, notifications.size)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            // Scrollable notifications section (takes remaining space)
                            LazyColumn(
                                state = innerLazyListState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (notifications.isNotEmpty()) {
                                    items(notifications.size) { index ->
                                        val notification = notifications[index]
                                        NotificationPreviewCard(
                                            sender = notification.senderName ?: notification.title,
                                            text = notification.content,
                                            timestamp = formatDate(notification.timestamp),
                                            onClick = {
                                                if (notification.packageName == "org.telegram.messenger") {
                                                    context.sendBroadcast(Intent("dev.rcht.jist.GOTO_CHAT").apply {
                                                        putExtra("title", notification.title)
                                                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                                                    })
                                                }
                                                val intent = dev.rcht.jist.util.ChatIntentBuilder.buildChatIntent(
                                                    context, notification.packageName, notification.conversationKey, notification.title
                                                )
                                                if (intent != null) {
                                                    Log.d("SummaryDetail", "Launching ${notification.conversationKey}")
                                                    context.startActivity(intent)
                                                }
                                            }
                                        )
                                    }
                                }

                                item { Spacer(modifier = Modifier.height(72.dp)) }
                            }
                        }
                    }
                }

                val infiniteTransition = rememberInfiniteTransition()
                val fabAngle = infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing)
                    )
                )
                val fabAlpha = animateFloatAsState(
                    targetValue = if (isReSummarizing) 0.4f else 1f,
                    animationSpec = tween(300),
                    label = "fabAlpha"
                )
                FloatingActionButton(
                    onClick = { if (!isReSummarizing) onReSummarize() },
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isReSummarizing) 0.6f else 1f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.summary_resummarize),
                        modifier = Modifier
                            .graphicsLayer(rotationZ = if (isReSummarizing) fabAngle.value else 0f)
                            .graphicsLayer(alpha = fabAlpha.value)
                    )
                }
            }
        }
    }
}

@Composable
fun WaveText(text: String, transition: androidx.compose.animation.core.InfiniteTransition) {
    Row(
        modifier = Modifier.padding(16.dp)
    ) {
        text.forEachIndexed { index, char ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, delayMillis = index * 120, easing = LinearEasing)
                )
            )
            Text(
                text = char.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
        }
    }
}

@Composable
private fun NotificationPreviewCard(
    sender: String,
    text: String,
    timestamp: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sender,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

private fun enhancePriorityLabels(text: String): String {
    return text
        .replace(Regex("(?m)^(\\s*[-•*]\\s*)(紧急/时效性强)"), "$1🔴 $2")
        .replace(Regex("(?m)^(\\s*[-•*]\\s*)(重要事件)"), "$1🟡 $2")
        .replace(Regex("(?m)^(\\s*[-•*]\\s*)(其他观点)"), "$1🔵 $2")
        .replace(Regex("(?m)^(\\s*[-•*]\\s*)(其他)"), "$1🔵 $2")
}
