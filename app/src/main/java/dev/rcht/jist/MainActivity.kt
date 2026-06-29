package dev.rcht.jist

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import dev.rcht.jist.ui.JistApp
import dev.rcht.jist.ui.theme.JistTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val deepLinkSummaryId = mutableStateOf<String?>(null)
    private val navTrigger = mutableStateOf(0L)

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

        val sid = intent.getStringExtra("summary_id")
        deepLinkSummaryId.value = sid
        navTrigger.value = sid?.toLongOrNull() ?: 0L
        Log.d(TAG, "onCreate: summary_id=$sid")
        setContent {
            JistTheme {
                JistApp(deepLinkSummaryId = "$sid:${navTrigger.value}")
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val sid = intent.getStringExtra("summary_id")
        deepLinkSummaryId.value = sid
        val ts = System.currentTimeMillis()
        Log.d(TAG, "onNewIntent: summary_id=$sid ts=$ts")
        navTrigger.value = ts
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}