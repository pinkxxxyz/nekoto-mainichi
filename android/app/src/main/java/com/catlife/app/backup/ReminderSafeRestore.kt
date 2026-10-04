package com.catlife.app.backup

import com.catlife.app.data.TodoItem

// Cancel old IDs while they are still in the transaction's rows. New alarms are
// scheduled only after commit, so a crash never leaves an untraceable alarm ID.
class ReminderSafeRestore(
    private val replaceRows: suspend (UserBackup, (List<TodoItem>) -> Unit) -> Unit,
    private val cancel: (TodoItem) -> Unit,
    private val schedule: (TodoItem) -> Unit,
) {
    suspend fun replace(snapshot: UserBackup) {
        replaceRows(snapshot) { previous -> previous.forEach(cancel) }
        snapshot.todos.forEach(schedule)
    }
}
