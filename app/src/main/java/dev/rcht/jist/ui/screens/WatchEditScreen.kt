package dev.rcht.jist.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.rcht.jist.R
import dev.rcht.jist.ui.components.GlassScaffold
import dev.rcht.jist.ui.theme.JistCyan
import dev.rcht.jist.ui.theme.JistPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WatchEditScreen(
    title: String,
    description: String,
    keywords: List<String>,
    matchMode: String,
    isSaving: Boolean,
    isGeneratingKeywords: Boolean,
    generatedKeywords: List<String>,
    error: String?,
    isEditing: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onMatchModeChange: (String) -> Unit,
    onGenerateKeywords: () -> Unit,
    onConfirmGeneratedKeywords: () -> Unit,
    onDiscardGeneratedKeywords: () -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isEditing) stringResource(R.string.watch_edit) else stringResource(R.string.watch_new),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.watch_content_desc_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.watch_save))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            FormSection(stringResource(R.string.watch_title_label)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.watch_title_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }

            FormSection(stringResource(R.string.watch_description_label)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    placeholder = { Text(stringResource(R.string.watch_description_hint)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }

            FormSection(stringResource(R.string.watch_keywords_label)) {
                KeywordInputSection(
                    keywords = keywords,
                    onAddKeyword = onAddKeyword,
                    onRemoveKeyword = onRemoveKeyword,
                    onGenerateKeywords = onGenerateKeywords,
                    isGenerating = isGeneratingKeywords,
                    generatedKeywords = generatedKeywords,
                    onConfirmGenerated = onConfirmGeneratedKeywords,
                    onDiscardGenerated = onDiscardGeneratedKeywords
                )
            }

            FormSection(stringResource(R.string.watch_match_mode_label)) {
                MatchModeSelector(
                    selected = matchMode,
                    onSelect = onMatchModeChange
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text(stringResource(R.string.watch_error_title)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = onClearError) { Text(stringResource(R.string.watch_error_ok)) }
            }
        )
    }
}

@Composable
private fun FormSection(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordInputSection(
    keywords: List<String>,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onGenerateKeywords: () -> Unit = {},
    isGenerating: Boolean = false,
    generatedKeywords: List<String> = emptyList(),
    onConfirmGenerated: () -> Unit = {},
    onDiscardGenerated: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    var showDuplicateHint by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it; showDuplicateHint = false },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.watch_keyword_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    val trimmed = inputText.trim()
                    if (trimmed.isNotBlank()) {
                        if (keywords.contains(trimmed)) {
                            showDuplicateHint = true
                        } else {
                            onAddKeyword(trimmed)
                            inputText = ""
                        }
                    }
                }
            ),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            onClick = {
                val trimmed = inputText.trim()
                if (trimmed.isNotBlank()) {
                    if (keywords.contains(trimmed)) {
                        showDuplicateHint = true
                    } else {
                        onAddKeyword(trimmed)
                        inputText = ""
                    }
                }
            },
            enabled = inputText.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = JistCyan.copy(alpha = 0.15f),
                contentColor = JistCyan
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.watch_content_desc_add),
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.watch_keyword_add))
        }
    }

    if (showDuplicateHint) {
        Text(
            stringResource(R.string.watch_keyword_duplicate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    Spacer(Modifier.height(8.dp))

    if (keywords.isEmpty()) {
        Text(
            stringResource(R.string.watch_keyword_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.Top
        ) {
            keywords.forEach { keyword ->
                KeywordChip(
                    text = keyword,
                    onRemove = { onRemoveKeyword(keyword) }
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onGenerateKeywords,
        enabled = !isGenerating,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        if (isGenerating) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.watch_generating_keywords))
        } else {
            Text(stringResource(R.string.watch_generate_keywords))
        }
    }

    if (generatedKeywords.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.watch_ai_generated_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = onConfirmGenerated,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.watch_use_keywords), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onDiscardGenerated,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.Top
                ) {
                    generatedKeywords.forEach { kw ->
                        Text(
                            text = kw,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordChip(
    text: String,
    onRemove: () -> Unit
) {
    var visible by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally()
    ) {
        AssistChip(
            onClick = {},
            label = { Text(text, style = MaterialTheme.typography.labelMedium) },
            trailingIcon = {
                IconButton(
                    onClick = {
                        visible = false
                        scope.launch {
                            delay(300)
                            onRemove()
                        }
                    },
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.watch_content_desc_delete),
                        modifier = Modifier.size(14.dp)
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            colors = AssistChipDefaults.assistChipColors(
                containerColor = JistPurple.copy(alpha = 0.15f),
                labelColor = MaterialTheme.colorScheme.onSurface
            ),
            border = AssistChipDefaults.assistChipBorder(
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                enabled = true
            )
        )
    }
}

@Composable
private fun MatchModeSelector(selected: String, onSelect: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onSelect("KEYWORD_ONLY") }
            ) {
                RadioButton(
                    selected = selected == "KEYWORD_ONLY",
                    onClick = { onSelect("KEYWORD_ONLY") }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.watch_match_keyword),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.watch_match_keyword_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onSelect("AI_SEMANTIC") }
            ) {
                RadioButton(
                    selected = selected == "AI_SEMANTIC",
                    onClick = { onSelect("AI_SEMANTIC") }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.watch_match_ai),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.watch_match_ai_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
