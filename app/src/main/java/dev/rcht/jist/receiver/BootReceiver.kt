package dev.rcht.jist.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.rcht.jist.worker.SummaryWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Schedule periodic work on device boot
            context?.let { SummaryWorker.schedule(it) }
        }
    }
}
