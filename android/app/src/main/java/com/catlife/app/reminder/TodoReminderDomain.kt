package com.catlife.app.reminder

import com.catlife.app.data.TodoItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

sealed interface ReminderCommand {
    data class Cancel(val todoId: Long) : ReminderCommand
    data class Schedule(val item: TodoItem, val triggerAtMillis: Long) : ReminderCommand
}

fun reminderAtMillis(
    item: TodoItem,
    zoneId: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): Long? {
    if (!item.reminderEnabled || item.completed) return null
    val date = runCatching { LocalDate.parse(item.deadlineDate) }.getOrNull() ?: return null
    val timeText = item.deadlineTime?.takeIf(String::isNotBlank)
        ?: item.reminderTime?.takeIf(String::isNotBlank)
        ?: return null
    val time = runCatching { LocalTime.parse(timeText) }.getOrNull() ?: return null
    val trigger = LocalDateTime.of(date, time).atZone(zoneId).toInstant().toEpochMilli()
    return trigger.takeIf { it > nowMillis }
}

fun updateReminderCommands(
    old: TodoItem?,
    updated: TodoItem,
    zoneId: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): List<ReminderCommand> = buildList {
    if (old != null && old.id > 0) add(ReminderCommand.Cancel(old.id))
    reminderAtMillis(updated, zoneId, nowMillis)?.let {
        add(ReminderCommand.Schedule(updated, it))
    }
}

fun deleteReminderCommands(item: TodoItem): List<ReminderCommand> =
    item.id.takeIf { it > 0 }?.let { listOf(ReminderCommand.Cancel(it)) }.orEmpty()

fun bootReminderItems(
    items: List<TodoItem>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): List<TodoItem> = items.filter { reminderAtMillis(it, zoneId, nowMillis) != null }

fun reminderRequestCode(todoId: Long): Int =
    ((todoId xor (todoId ushr 32)).toInt() and Int.MAX_VALUE)
