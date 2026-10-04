package com.catlife.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.catlife.app.KorokkeLifeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val application = context.applicationContext as KorokkeLifeApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val scheduler = TodoReminderScheduler(application)
                bootReminderItems(application.database.todoDao().getAllOnce(), nowMillis = now)
                    .forEach { scheduler.schedule(it, nowMillis = now) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
