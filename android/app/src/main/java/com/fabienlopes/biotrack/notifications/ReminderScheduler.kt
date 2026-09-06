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
import androidx.core.content.edit
import com.fabienlopes.biotrack.R
import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.LocalStore
import com.fabienlopes.biotrack.data.ProtocolCompletion
import com.fabienlopes.biotrack.data.Reminder
import com.fabienlopes.biotrack.data.ReminderTargetKind
import com.fabienlopes.biotrack.data.SupplementIntake
import com.fabienlopes.biotrack.domain.Planner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

object ReminderScheduler {
    const val channelId = "ajuste-reminders"
    internal const val actionDone = "com.fabienlopes.biotrack.reminder.DONE"
    internal const val actionSnooze = "com.fabienlopes.biotrack.reminder.SNOOZE"
    internal const val extraTitle = "title"
    internal const val extraNotes = "notes"
    internal const val extraReminderId = "reminder_id"
    internal const val extraNotificationBaseId = "notification_base_id"
    internal const val extraTargetKind = "target_kind"
    internal const val extraTargetId = "target_id"
    internal const val extraSnoozeMinutes = "snooze_minutes"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(channelId, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, reminder: Reminder) {
        cancel(context, reminder)
        if (!reminder.enabled) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val weekdays = reminder.weekdays.ifEmpty { listOf(0) }
        weekdays.forEach { weekday ->
            val pendingIntent = checkNotNull(reminderPendingIntent(
                context,
                reminder,
                requestCode(reminder.notificationBaseId, weekday),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                nextTriggerMillis(reminder, weekday),
                if (weekday == 0) AlarmManager.INTERVAL_DAY else AlarmManager.INTERVAL_DAY * 7,
                pendingIntent
            )
        }
    }

    fun cancel(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        // Clear recurring and snoozed variants so editing or disabling cannot leave orphan alarms.
        cancellationVariants().forEach { variant ->
            val pendingIntent = reminderPendingIntent(
                context,
                reminder,
                requestCode(reminder.notificationBaseId, variant),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    fun rescheduleAll(context: Context) {
        createChannel(context)
        LocalStore(context).load().reminders.forEach { reminder ->
            if (reminder.enabled) schedule(context, reminder) else cancel(context, reminder)
        }
    }

    fun snooze(context: Context, reminder: Reminder, minutes: Int, now: Long = System.currentTimeMillis()) {
        val safeMinutes = minutes.coerceIn(1, 24 * 60)
        val pendingIntent = checkNotNull(reminderPendingIntent(
            context,
            reminder,
            requestCode(reminder.notificationBaseId, 1000 + safeMinutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            now + safeMinutes * 60_000L,
            pendingIntent
        )
    }

    internal fun nextTriggerMillis(
        reminder: Reminder,
        targetMondayDay: Int,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val now = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val target = (now.clone() as Calendar).apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, reminder.hour.coerceIn(0, 23))
            set(Calendar.MINUTE, reminder.minute.coerceIn(0, 59))
        }
        if (targetMondayDay == 0) {
            if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.DAY_OF_YEAR, 1)
            return target.timeInMillis
        }
        val currentMondayDay = ((now.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        var distance = (targetMondayDay.coerceIn(1, 7) - currentMondayDay + 7) % 7
        if (distance == 0 && target.timeInMillis <= now.timeInMillis) distance = 7
        target.add(Calendar.DAY_OF_YEAR, distance)
        return target.timeInMillis
    }

    internal fun requestCode(baseId: String, variant: Int): Int =
        (baseId.hashCode() * 31 + variant).and(0x7FFFFFFF)

    internal fun cancellationVariants(): Set<Int> =
        (0..7).toSet() + setOf(1015, 1030, 1060)

    internal fun reminderFromIntent(context: Context, intent: Intent): Reminder = Reminder(
        id = intent.getStringExtra(extraReminderId).orEmpty(),
        notificationBaseId = intent.getStringExtra(extraNotificationBaseId)
            ?: "reminder-${intent.getStringExtra(extraReminderId).orEmpty()}",
        title = intent.getStringExtra(extraTitle) ?: context.getString(R.string.default_reminder_title),
        hour = 8,
        minute = 0,
        notes = intent.getStringExtra(extraNotes),
        targetKind = intent.getStringExtra(extraTargetKind)?.let { raw ->
            ReminderTargetKind.entries.firstOrNull { it.name == raw }
        },
        targetId = intent.getStringExtra(extraTargetId)
    )

    private fun reminderPendingIntent(
        context: Context,
        reminder: Reminder,
        requestCode: Int,
        flags: Int
    ): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).putReminder(reminder)
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    internal fun Intent.putReminder(reminder: Reminder): Intent = apply {
        putExtra(extraTitle, reminder.title)
        putExtra(extraNotes, reminder.notes)
        putExtra(extraReminderId, reminder.id)
        putExtra(extraNotificationBaseId, reminder.notificationBaseId)
        putExtra(extraTargetKind, reminder.targetKind?.name)
        putExtra(extraTargetId, reminder.targetId)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.createChannel(context)
        val reminder = ReminderScheduler.reminderFromIntent(context, intent)
        val notificationId = reminder.id.hashCode().and(0x7FFFFFFF)
        val builder = NotificationCompat.Builder(context, ReminderScheduler.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(reminder.notes ?: context.getString(R.string.notification_default_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (reminder.targetKind != null && reminder.targetId != null) {
            builder.addAction(R.drawable.ic_notification, context.getString(R.string.notification_done), actionPendingIntent(context, reminder, ReminderScheduler.actionDone, 0))
        }
        listOf(15, 30, 60).forEach { minutes ->
            builder.addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_snooze, minutes),
                actionPendingIntent(context, reminder, ReminderScheduler.actionSnooze, minutes)
            )
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun actionPendingIntent(context: Context, reminder: Reminder, action: String, minutes: Int): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .apply { ReminderScheduler.run { putReminder(reminder) } }
            .putExtra(ReminderScheduler.extraSnoozeMinutes, minutes)
        return PendingIntent.getBroadcast(
            context,
            ReminderScheduler.requestCode(reminder.notificationBaseId, action.hashCode() + minutes),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val reminder = ReminderScheduler.reminderFromIntent(context, intent)
                when (intent.action) {
                    ReminderScheduler.actionDone -> {
                        val store = LocalStore(context)
                        store.save(ReminderAction.applyDone(store.load(), reminder, System.currentTimeMillis()))
                        context.getSharedPreferences("biotrack-preferences", 0).edit {
                            putBoolean("snapshotUpdatedExternally", true)
                        }
                    }
                    ReminderScheduler.actionSnooze -> ReminderScheduler.snooze(
                        context,
                        reminder,
                        intent.getIntExtra(ReminderScheduler.extraSnoozeMinutes, 15)
                    )
                }
                context.getSystemService(NotificationManager::class.java)
                    .cancel(reminder.id.hashCode().and(0x7FFFFFFF))
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class ReminderSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in supportedActions) return
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                ReminderScheduler.rescheduleAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

object ReminderAction {
    fun applyDone(snapshot: AppSnapshot, reminder: Reminder, now: Long): AppSnapshot = when (reminder.targetKind) {
        ReminderTargetKind.PROTOCOL -> {
            val targetId = reminder.targetId ?: return snapshot
            if (snapshot.protocolCompletions.any { it.protocolId == targetId && it.completed && Planner.sameDay(it.date, now) }) snapshot
            else snapshot.copy(protocolCompletions = snapshot.protocolCompletions + ProtocolCompletion(protocolId = targetId, date = now))
        }
        ReminderTargetKind.SUPPLEMENT -> {
            val targetId = reminder.targetId ?: return snapshot
            if (snapshot.supplementIntakes.any { it.supplementId == targetId && it.taken && Planner.sameDay(it.date, now) }) snapshot
            else {
                val supplement = snapshot.supplements.firstOrNull { it.id == targetId }
                snapshot.copy(supplementIntakes = snapshot.supplementIntakes + SupplementIntake(
                    supplementId = targetId,
                    date = now,
                    dose = supplement?.dose,
                    brand = supplement?.brand
                ))
            }
        }
        null -> snapshot
    }
}
