package com.catlife.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Entity(tableName = "todos")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val completed: Boolean = false,
    val deadlineDate: String? = null,
    val deadlineTime: String? = null,
    val reminderDateTime: String? = null,
    val reminderEnabled: Boolean = false,
    val reminderTime: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val purchased: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

fun sortTodos(items: List<TodoItem>) = items.sortedWith(compareBy<TodoItem> { it.completed }.thenBy { it.createdAt })
fun sortShopping(items: List<ShoppingItem>) = items.sortedWith(compareBy<ShoppingItem> { it.purchased }.thenBy { it.createdAt })

fun TodoItem.isOverdue(today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now()): Boolean {
    if (completed || deadlineDate == null) return false
    val deadline = runCatching {
        LocalDateTime.of(LocalDate.parse(deadlineDate), deadlineTime?.let(LocalTime::parse) ?: LocalTime.of(23, 59))
    }.getOrNull() ?: return false
    return deadline.isBefore(LocalDateTime.of(today, now))
}

fun TodoItem.deadlineLabel(): String? = deadlineDate?.let {
    val date = runCatching { LocalDate.parse(it) }.getOrNull() ?: return@let null
    if (deadlineTime == null) "${date.monthValue}/${date.dayOfMonth}まで"
    else "${date.monthValue}/${date.dayOfMonth} ${deadlineTime.take(5)}まで"
}
