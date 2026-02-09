package dev.rcht.jist.ui.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class AboutViewModel(application: Application) : AndroidViewModel(application) {

    private val _appVersion = MutableLiveData<String>().apply {
        value = "v1.0"
    }
    val appVersion: LiveData<String> = _appVersion

    private val _description = MutableLiveData<String>().apply {
        value = "Jist - Intelligent Notification Summarizer\n\n" +
                "Capture and summarize your notifications with AI-powered summaries.\n\n" +
                "GitHub: github.com/rchtgzm/jist"
    }
    val description: LiveData<String> = _description

    private val _buildInfo = MutableLiveData<String>().apply {
        value = "Build: debug"
    }
    val buildInfo: LiveData<String> = _buildInfo
}
