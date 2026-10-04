package com.catlife.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catlife.app.data.*
import com.catlife.app.reminder.TodoReminderScheduler
import com.catlife.app.reminder.deleteReminderCommands
import com.catlife.app.reminder.updateReminderCommands
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as KorokkeLifeApplication).database
    private val reminderScheduler = TodoReminderScheduler(app)
    val todos = db.todoDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val shopping = db.shoppingDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun saveTodo(
        title: String,
        old: TodoItem? = null,
        deadlineDate: String? = null,
        deadlineTime: String? = null,
        reminderEnabled: Boolean = false,
        reminderTime: String? = null,
    ) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        if (old == null) {
            val draft = TodoItem(
                title = title.trim(),
                deadlineDate = deadlineDate,
                deadlineTime = deadlineTime,
                reminderEnabled = reminderEnabled,
                reminderTime = reminderTime,
            )
            val saved = draft.copy(id = db.todoDao().insert(draft))
            reminderScheduler.apply(updateReminderCommands(null, saved))
        } else {
            val saved = old.copy(
                title = title.trim(),
                deadlineDate = deadlineDate,
                deadlineTime = deadlineTime,
                reminderEnabled = reminderEnabled,
                reminderTime = reminderTime,
            )
            db.todoDao().update(saved)
            reminderScheduler.apply(updateReminderCommands(old, saved))
        }
    }
    fun toggle(item: TodoItem) = viewModelScope.launch {
        val updated = item.copy(
            completed = !item.completed,
            reminderEnabled = if (!item.completed) false else item.reminderEnabled,
            reminderDateTime = if (!item.completed) null else item.reminderDateTime,
        )
        db.todoDao().update(updated)
        reminderScheduler.apply(updateReminderCommands(item, updated))
    }
    fun delete(item: TodoItem) = viewModelScope.launch {
        reminderScheduler.apply(deleteReminderCommands(item))
        db.todoDao().delete(item)
    }
    fun deleteCompleted() = viewModelScope.launch {
        db.todoDao().getCompletedOnce().forEach { reminderScheduler.apply(deleteReminderCommands(it)) }
        db.todoDao().deleteCompleted()
    }
    fun saveShopping(title: String, old: ShoppingItem? = null) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        if (old == null) db.shoppingDao().insert(ShoppingItem(title = title.trim())) else db.shoppingDao().update(old.copy(title = title.trim()))
    }
    fun toggle(item: ShoppingItem) = viewModelScope.launch { db.shoppingDao().update(item.copy(purchased = !item.purchased)) }
    fun delete(item: ShoppingItem) = viewModelScope.launch { db.shoppingDao().delete(item) }
    fun deletePurchased() = viewModelScope.launch { db.shoppingDao().deletePurchased() }
}
