package dev.rcht.jist

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import dev.rcht.jist.ui.JistApp
import dev.rcht.jist.ui.theme.JistTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val deepLinkSummaryId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.getStringExtra("trigger_summarize") == "true" || intent.getBooleanExtra("trigger_summarize", false)) {
            Log.d(TAG, "Trigger immediate summarization via intent")
            val app = application as JistApplication
            lifecycleScope.launch(Dispatchers.IO) {
                val results = app.summaryEngine.summarizeAllPending()
                Log.d(TAG, "Immediate summarization done: ${results.size} results")
            }
        }
        val conversationKey = intent.getStringExtra("summarize_conversation")
        if (conversationKey != null) {
            Log.d(TAG, "Trigger re-summarize for conversation: $conversationKey")
            val app = application as JistApplication
            lifecycleScope.launch(Dispatchers.IO) {
                val result = app.summaryEngine.summarizeConversation(conversationKey, includeSummarized = true)
                Log.d(TAG, "Re-summarize result: $result")
            }
        }

        Log.d(TAG, "onCreate: summary_id=${intent.getStringExtra("summary_id")}")
        deepLinkSummaryId.value = intent.getStringExtra("summary_id")
        setContent {
            val key by remember { derivedStateOf {
                val id = deepLinkSummaryId.value
                if (id != null) "$id:${System.currentTimeMillis()}" else null
            } }
            JistTheme {
                JistApp(deepLinkSummaryId = key)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val sid = intent.getStringExtra("summary_id")
        Log.d(TAG, "onNewIntent: summary_id=$sid")
        deepLinkSummaryId.value = sid
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}