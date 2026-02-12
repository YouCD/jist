package dev.rcht.jist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.rcht.jist.ui.JistApp
import dev.rcht.jist.ui.theme.JistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Do not request notification permission on startup; onboarding will request it step-by-step
        setContent {
            JistTheme {
                JistApp()
            }
        }
    }
}