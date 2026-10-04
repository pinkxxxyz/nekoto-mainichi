package com.catlife.app.backup

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

interface BackupStore {
    suspend fun read(): UserBackup
    suspend fun replace(snapshot: UserBackup)
}

interface RestoreJournal {
    suspend fun read(): UserBackup?
    suspend fun write(snapshot: UserBackup)
    suspend fun clear()
}

// The durable undo snapshot is removed only after a complete replacement or rollback.
class RestoreCoordinator(private val store: BackupStore, private val journal: RestoreJournal) {
    suspend fun recover() = withContext(NonCancellable) {
        journal.read()?.let { previous ->
            store.replace(previous)
            journal.clear()
        }
    }

    suspend fun restore(snapshot: UserBackup) = withContext(NonCancellable) {
        BackupCodec.validate(snapshot)
        recover()
        val previous = store.read()
        journal.write(previous)
        try {
            store.replace(snapshot)
            journal.clear()
        } catch (error: Exception) {
            try {
                store.replace(previous)
                journal.clear()
            } catch (rollbackError: Exception) {
                error.addSuppressed(rollbackError)
            }
            throw error
        }
    }
}
