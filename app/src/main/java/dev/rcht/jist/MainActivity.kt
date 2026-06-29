package dev.rcht.jist

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import dev.rcht.jist.ui.JistApp
import dev.rcht.jist.ui.theme.JistTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
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

        setContent {
            JistTheme {
                JistApp()
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}