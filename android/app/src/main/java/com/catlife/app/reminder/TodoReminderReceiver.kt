package com.catlife.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.catlife.app.MainActivity
import com.catlife.app.R
import com.catlife.app.settings.CatVoiceEnabledKey
import com.catlife.app.settings.korokkeLifeDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

const val TODO_REMINDER_CAT_CHANNEL_ID = "todo_reminders_cat_v3"
const val TODO_REMINDER_DEFAULT_CHANNEL_ID = "todo_reminders_default_v2"

fun todoReminderChannelId(catVoiceEnabled: Boolean): String =
    if (catVoiceEnabled) TODO_REMINDER_CAT_CHANNEL_ID else TODO_REMINDER_DEFAULT_CHANNEL_ID

fun createTodoReminderChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val notificationAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    val catChannel = NotificationChannel(
        TODO_REMINDER_CAT_CHANNEL_ID,
        "やることリマインダー（猫の鳴き声）",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "猫の鳴き声でTODOの期日をお知らせします"
        setSound(null, null)
    }
    val defaultChannel = NotificationChannel(
        TODO_REMINDER_DEFAULT_CHANNEL_ID,
        "やることリマインダー",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "TODOの期日をお知らせします"
        setSound(Settings.System.DEFAULT_NOTIFICATION_URI, notificationAudioAttributes)
    }
    context.getSystemService(NotificationManager::class.java).apply {
        createNotificationChannel(catChannel)
        createNotificationChannel(defaultChannel)
    }
}

class TodoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val todoId = intent.getLongExtra(EXTRA_TODO_ID, 0L)
        val title = intent.getStringExtra(EXTRA_TODO_TITLE)?.takeIf { it.isNotBlank() } ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val catVoiceEnabled = context.korokkeLifeDataStore.data.first()[CatVoiceEnabledKey] ?: true
                deliverTodoReminder(
                    catVoiceEnabled,
                    post = { channelId -> postNotification(context, todoId, title, channelId) },
                    playCatSound = { playCatNotificationSound(context.applicationContext) },
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postNotification(context: Context, todoId: Long, title: String, channelId: String) {
        val openApp = PendingIntent.getActivity(
            context,
            reminderRequestCode(todoId),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            // Also silence previously created cat channels whose sound cannot be updated.
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("やることリマインダー")
            .setContentText(title)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(reminderRequestCode(todoId), notification)
    }

    companion object {
        private const val EXTRA_TODO_ID = "todo_id"
        private const val EXTRA_TODO_TITLE = "todo_title"

        fun intent(context: Context, todoId: Long, title: String) =
            Intent(context, TodoReminderReceiver::class.java).apply {
                action = "com.catlife.app.action.TODO_REMINDER.$todoId"
                putExtra(EXTRA_TODO_ID, todoId)
                putExtra(EXTRA_TODO_TITLE, title)
            }
    }
}

// One delivery posts first, then makes at most one app playback request.
internal suspend fun deliverTodoReminder(
    catVoiceEnabled: Boolean,
    post: (String) -> Unit,
    playCatSound: suspend () -> Unit,
) {
    post(todoReminderChannelId(catVoiceEnabled))
    if (catVoiceEnabled) playCatSound()
}

private suspend fun playCatNotificationSound(context: Context) = withContext(Dispatchers.Main) {
    Log.d("TodoReminder", "Cat SoundPool: begin")
    val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .build()
    var playbackStarted = false
    try {
        // Bound loading, then allow the full playback interval before release.
        withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine<Int> { continuation ->
                var handled = false
                soundPool.setOnLoadCompleteListener { pool, sampleId, status ->
                    Log.d("TodoReminder", "Cat SoundPool: loaded status=$status")
                    if (continuation.isActive && !handled) {
                        handled = true
                        val id = if (status == 0) {
                            try {
                                pool.play(sampleId, 1f, 1f, 1, 0, 1f).also {
                                    playbackStarted = it != 0
                                    Log.d("TodoReminder", "Cat SoundPool: playing streamId=$it")
                                }
                            } catch (error: RuntimeException) {
                                Log.w("TodoReminder", "Cannot play cat notification sound", error)
                                0
                            }
                        } else {
                            Log.w("TodoReminder", "Cat notification load error: $status")
                            0
                        }
                        continuation.resume(id)
                    }
                }
                val sampleId = soundPool.load(context, R.raw.notification, 1)
                if (sampleId == 0 && continuation.isActive && !handled) {
                    handled = true
                    Log.w("TodoReminder", "Cannot load cat notification sound")
                    continuation.resume(0)
                }
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: RuntimeException) {
        Log.w("TodoReminder", "Cannot play cat notification sound", error)
    } finally {
        if (playbackStarted) {
            withContext(NonCancellable) { delay(2_000L) }
        }
        soundPool.setOnLoadCompleteListener(null)
        Log.d("TodoReminder", "Cat SoundPool: releasing")
        soundPool.release()
    }
    Unit
}
