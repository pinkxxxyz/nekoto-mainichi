package com.catlife.app

import android.app.Application
import com.catlife.app.data.AppDatabase
import com.catlife.app.reminder.createTodoReminderChannel

class KorokkeLifeApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val backupService by lazy { com.catlife.app.backup.AndroidBackupService(this, database) }

    override fun onCreate() {
        super.onCreate()
        // Finish an interrupted restore before Activity or broadcast consumers run.
        // A failed recovery aborts startup instead of exposing partly restored data.
        if (com.catlife.app.backup.AndroidBackupService.hasPendingRestore(this)) {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                backupService.recover()
            }
        }
        createTodoReminderChannel(this)
    }
}
