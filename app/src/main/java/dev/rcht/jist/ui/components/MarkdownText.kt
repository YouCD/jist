package dev.rcht.jist.ui.components

import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    val context = LocalContext.current
    val markwon = remember { Markwon.create(context) }
    val textColor = MaterialTheme.colorScheme.onSurface

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor.toArgb())
                textSize = 14f
                setLineSpacing(4f, 1f)
            }
        },
        modifier = modifier,
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
            textView.maxLines = maxLines
            textView.movementMethod = null
        }
    )
}