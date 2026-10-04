package com.catlife.app

import com.catlife.app.data.TodoItem
import com.catlife.app.reminder.ReminderCommand
import com.catlife.app.reminder.bootReminderItems
import com.catlife.app.reminder.deleteReminderCommands
import com.catlife.app.reminder.reminderAtMillis
import com.catlife.app.reminder.reminderRequestCode
import com.catlife.app.reminder.TODO_REMINDER_CAT_CHANNEL_ID
import com.catlife.app.reminder.TODO_REMINDER_DEFAULT_CHANNEL_ID
import com.catlife.app.reminder.todoReminderChannelId
import com.catlife.app.reminder.updateReminderCommands
import com.catlife.app.reminder.AlarmPrecision
import com.catlife.app.reminder.alarmPrecisionFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.catlife.app.reminder.deliverTodoReminder
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TodoReminderTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val now = millis("2026-10-05T08:00")

    @Test fun dateAndDeadlineTimeProduceNotificationTime() {
        val item = todo(deadlineTime = "17:30", reminderTime = null)
        assertEquals(millis("2026-10-05T17:30"), reminderAtMillis(item, zone, now))
    }

    @Test fun dateOnlyUsesIndependentReminderTime() {
        val item = todo(deadlineTime = null, reminderTime = "09:00")
        assertEquals(millis("2026-10-05T09:00"), reminderAtMillis(item, zone, now))
        assertNull(item.deadlineTime)
    }

    @Test fun disabledReminderIsNotScheduled() {
        assertNull(reminderAtMillis(todo(reminderEnabled = false), zone, now))
    }

    @Test fun pastOrInvalidReminderIsNotScheduled() {
        assertNull(reminderAtMillis(todo(deadlineTime = "07:59"), zone, now))
        assertNull(reminderAtMillis(todo(deadlineDate = "bad", deadlineTime = "17:30"), zone, now))
        assertNull(reminderAtMillis(todo(deadlineTime = null, reminderTime = null), zone, now))
    }

    @Test fun updateCancelsOldAlarmThenSchedulesNewAlarm() {
        val old = todo(id = 42, deadlineTime = "17:30")
        val updated = old.copy(deadlineTime = "18:00")
        assertEquals(
            listOf(ReminderCommand.Cancel(42), ReminderCommand.Schedule(updated, millis("2026-10-05T18:00"))),
            updateReminderCommands(old, updated, zone, now),
        )
    }

    @Test fun deletingOrCompletingCancelsAlarm() {
        val item = todo(id = 42, deadlineTime = "17:30")
        assertEquals(listOf(ReminderCommand.Cancel(42)), deleteReminderCommands(item))
        assertEquals(
            listOf(ReminderCommand.Cancel(42)),
            updateReminderCommands(item, item.copy(completed = true), zone, now),
        )
    }

    @Test fun existingTodoDefaultsToReminderDisabled() {
        assertFalse(TodoItem(title = "既存").reminderEnabled)
    }

    @Test fun bootRestoresOnlyFutureEnabledIncompleteReminders() {
        val future = todo(id = 1, deadlineTime = "17:30")
        val disabled = todo(id = 2, reminderEnabled = false)
        val past = todo(id = 3, deadlineTime = "07:59")
        val completed = todo(id = 4, completed = true)
        val invalid = todo(id = 5, deadlineDate = "bad")
        assertEquals(listOf(future), bootReminderItems(listOf(future, disabled, past, completed, invalid), zone, now))
    }

    @Test fun requestAndNotificationIdAreStablePerTodo() {
        assertEquals(reminderRequestCode(4_294_967_297L), reminderRequestCode(4_294_967_297L))
        assertTrue(reminderRequestCode(1) != reminderRequestCode(2))
    }

    @Test fun catVoiceSettingSelectsCatOrDefaultReminderChannel() {
        assertEquals("todo_reminders_cat_v3", todoReminderChannelId(catVoiceEnabled = true))
        assertEquals("todo_reminders_default_v2", todoReminderChannelId(catVoiceEnabled = false))
    }

    @Test fun catVoicePostsBeforePlayingExactlyOnce() = runBlocking {
        val events = mutableListOf<String>()
        deliverTodoReminder(true, { events += it }, { events += "play" })
        assertEquals(listOf(TODO_REMINDER_CAT_CHANNEL_ID, "play"), events)
    }

    @Test fun disabledCatVoicePostsWithoutAppPlayback() = runBlocking {
        val events = mutableListOf<String>()
        deliverTodoReminder(false, { events += it }, { events += "play" })
        assertEquals(listOf(TODO_REMINDER_DEFAULT_CHANNEL_ID), events)
    }

    @Test fun api31WithExactPermissionSelectsExactAlarm() {
        assertEquals(AlarmPrecision.EXACT, alarmPrecisionFor(apiLevel = 31, canScheduleExactAlarms = true))
    }

    @Test fun api31WithoutExactPermissionFallsBackToInexactAlarm() {
        assertEquals(AlarmPrecision.INEXACT, alarmPrecisionFor(apiLevel = 31, canScheduleExactAlarms = false))
    }

    @Test fun apiBefore31SelectsExactAlarmWithoutSpecialAccessCheck() {
        assertEquals(AlarmPrecision.EXACT, alarmPrecisionFor(apiLevel = 30, canScheduleExactAlarms = false))
    }

    private fun todo(
        id: Long = 1,
        deadlineDate: String = "2026-10-05",
        deadlineTime: String? = null,
        reminderEnabled: Boolean = true,
        reminderTime: String? = "09:00",
        completed: Boolean = false,
    ) = TodoItem(
        id = id,
        title = "牛乳を買う",
        completed = completed,
        deadlineDate = deadlineDate,
        deadlineTime = deadlineTime,
        reminderEnabled = reminderEnabled,
        reminderTime = reminderTime,
    )

    private fun millis(value: String): Long =
        LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
}
