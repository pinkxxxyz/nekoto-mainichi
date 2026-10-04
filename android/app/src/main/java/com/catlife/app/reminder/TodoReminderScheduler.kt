package com.catlife.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.catlife.app.data.TodoItem

enum class AlarmPrecision { EXACT, INEXACT }

fun alarmPrecisionFor(apiLevel: Int, canScheduleExactAlarms: Boolean): AlarmPrecision =
    if (apiLevel < 31 || canScheduleExactAlarms) AlarmPrecision.EXACT else AlarmPrecision.INEXACT

fun canScheduleExactTodoAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

class TodoReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(item: TodoItem, nowMillis: Long = System.currentTimeMillis()) {
        val triggerAtMillis = reminderAtMillis(item, nowMillis = nowMillis) ?: return
        scheduleAt(item, triggerAtMillis)
    }

    fun scheduleAt(item: TodoItem, triggerAtMillis: Long) {
        if (item.id <= 0 || triggerAtMillis <= System.currentTimeMillis()) return
        val operation = alarmPendingIntent(item)
        when (alarmPrecisionFor(Build.VERSION.SDK_INT, canScheduleExactTodoAlarms(context))) {
            AlarmPrecision.EXACT -> try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
            AlarmPrecision.INEXACT ->
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    fun cancel(todoId: Long) {
        if (todoId <= 0) return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderRequestCode(todoId),
            TodoReminderReceiver.intent(context, todoId, ""),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun apply(commands: List<ReminderCommand>) {
        commands.forEach { command ->
            when (command) {
                is ReminderCommand.Cancel -> cancel(command.todoId)
                is ReminderCommand.Schedule -> scheduleAt(command.item, command.triggerAtMillis)
            }
        }
    }

    private fun alarmPendingIntent(item: TodoItem): PendingIntent = PendingIntent.getBroadcast(
        context,
        reminderRequestCode(item.id),
        TodoReminderReceiver.intent(context, item.id, item.title),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
