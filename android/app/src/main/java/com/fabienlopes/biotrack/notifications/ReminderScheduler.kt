package com.fabienlopes.biotrack.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fabienlopes.biotrack.R
import com.fabienlopes.biotrack.data.Reminder
import java.util.Calendar

object ReminderScheduler {
    const val channelId = "biotrack-reminders"
    private const val extraTitle = "title"
    private const val extraReminderId = "reminder_id"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(channelId, "Rappels AJUSTE", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Rappels locaux de votre checklist AJUSTE"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, reminder: Reminder) {
        cancel(context, reminder)
        if (!reminder.enabled) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val weekdays = reminder.weekdays.ifEmpty { listOf(0) }
        weekdays.forEach { weekday ->
            val requestCode = requestCode(reminder, weekday)
            val trigger = nextTrigger(reminder, weekday)
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(extraTitle, reminder.title)
                putExtra(extraReminderId, reminder.id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                trigger,
                if (weekday == 0) AlarmManager.INTERVAL_DAY else AlarmManager.INTERVAL_DAY * 7,
                pendingIntent
            )
        }
    }

    fun cancel(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val weekdays = reminder.weekdays.ifEmpty { listOf(0) }
        weekdays.forEach { weekday ->
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode(reminder, weekday),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it); it.cancel() }
        }
    }

    private fun nextTrigger(reminder: Reminder, targetMondayDay: Int): Long {
        val now = Calendar.getInstance()
        val target = now.clone() as Calendar
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        target.set(Calendar.HOUR_OF_DAY, reminder.hour.coerceIn(0, 23))
        target.set(Calendar.MINUTE, reminder.minute.coerceIn(0, 59))
        if (targetMondayDay == 0) {
            if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.DAY_OF_YEAR, 1)
            return target.timeInMillis
        }
        val currentMondayDay = ((now.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        var distance = (targetMondayDay - currentMondayDay + 7) % 7
        if (distance == 0 && target.timeInMillis <= now.timeInMillis) distance = 7
        target.add(Calendar.DAY_OF_YEAR, distance)
        return target.timeInMillis
    }

    private fun requestCode(reminder: Reminder, weekday: Int): Int =
        (reminder.notificationBaseId.hashCode() * 31 + weekday).and(0x7FFFFFFF)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.createChannel(context)
        val title = intent.getStringExtra("title") ?: "Rappel AJUSTE"
        val notificationId = intent.getStringExtra("reminder_id")?.hashCode()?.and(0x7FFFFFFF) ?: title.hashCode()
        val notification = NotificationCompat.Builder(context, ReminderScheduler.channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText("Votre checklist AJUSTE vous attend.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
