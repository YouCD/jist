package dev.rcht.jist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.rcht.jist.ui.JistApp
import dev.rcht.jist.ui.theme.JistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JistTheme {
                JistApp()
            }
        }
    }
}