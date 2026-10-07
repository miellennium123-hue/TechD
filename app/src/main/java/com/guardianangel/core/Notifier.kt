package com.guardianangel.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.guardianangel.R
import com.guardianangel.ui.MainActivity
import com.guardianangel.ui.Screen
import com.guardianangel.ui.ShowUpActivity
import com.guardianangel.ui.SessionActivity
import com.guardianangel.ui.WatchActivity

/** Discreet mode keeps every notification neutral, so nothing explicit shows on the lock screen. */
object Notifier {
    private const val CHANNEL_ID = "reminders"
    const val ID_CHECK_IN = 1
    const val ID_PROOF = 2
    const val ID_MESSAGE = 3
    const val ID_SUMMON = 4
    const val ID_TASK = 5
    const val ID_PEEK = 6
    const val ID_REPORT = 7
    const val ID_WATCH = 8
    const val ID_SESSION = 9

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun checkIn(context: Context, line: String) = post(context, ID_CHECK_IN, line, "Time to check in.")
    fun proof(context: Context, line: String) = post(context, ID_PROOF, line, "You have something to do.")
    fun message(context: Context, line: String) = post(context, ID_MESSAGE, line, "You have a new message.")

    /** She peeks (round 60): every 5 minutes, so it replaces the last one and never buzzes. */
    fun peek(context: Context, line: String) =
        post(context, ID_PEEK, line, "You have a new message.", MainActivity.intent(context, Screen.GALLERY), silent = true)

    /** Daily report (round 68): tapping it opens her reports. Silent in quiet time. */
    fun report(context: Context, line: String, silent: Boolean) =
        post(context, ID_REPORT, line, "Your daily report is ready.", MainActivity.intent(context, Screen.REPORTS), silent = silent)

    /** Tapping it opens her task screen directly. */
    fun task(context: Context, line: String) =
        post(context, ID_TASK, line, "You have something to do.", MainActivity.intent(context, Screen.TASK))

    /** Round 77: tapping it plays her clip full screen. You have a minute. */
    fun watch(context: Context, line: String) =
        post(context, ID_WATCH, line, "You have something to do. Open within a minute.", WatchActivity.intent(context))

    /** Round 84: booked sessions and the ruin you owe. Tapping it opens her session screen. */
    fun session(context: Context, line: String, silent: Boolean = false) =
        post(context, ID_SESSION, line, "You have something to do.", SessionActivity.intent(context), silent = silent)

    /** Tapping it brings her up full screen. */
    fun summon(context: Context, line: String) =
        post(context, ID_SUMMON, line, "Please open the app.", ShowUpActivity.intent(context))

    /**
     * False when notifications are off for the app or for her channel. She doesn't set deadlines
     * she can't tell you about (see Rules.checkInAction).
     */
    fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
    fun cancelAll(context: Context) = NotificationManagerCompat.from(context).cancelAll()

    @SuppressLint("MissingPermission")
    private fun post(
        context: Context,
        id: Int,
        line: String,
        neutral: String,
        target: Intent = Intent(context, MainActivity::class.java),
        silent: Boolean = false,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val discreet = Guardian.config.value.discreetNotifications
        val open = PendingIntent.getActivity(
            context,
            id,
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reminder")
            .setContentText(neutral)
            .build()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPublicVersion(publicVersion)
            .setSilent(silent)
        if (discreet) {
            builder.setContentTitle("Reminder").setContentText(neutral)
        } else {
            builder.setContentTitle("Your Guardian Angel")
                .setContentText(line)
                .setStyle(NotificationCompat.BigTextStyle().bigText(line))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}
