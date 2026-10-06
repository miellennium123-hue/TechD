package com.guardianangel.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.guardianangel.data.ProofRequest
import com.guardianangel.receiver.AlarmReceiver
import kotlin.math.max
import kotlin.random.Random

/** Exact alarms for check-ins, proof deadlines and the chastity timer. */
object Scheduler {
    const val ACTION_CHECK_IN = "com.guardianangel.action.CHECK_IN"
    const val ACTION_PROOF_DEADLINE = "com.guardianangel.action.PROOF_DEADLINE"
    const val ACTION_CHASTITY_END = "com.guardianangel.action.CHASTITY_END"
    const val ACTION_TASK = "com.guardianangel.action.TASK"
    const val EXTRA_ID = "id"

    private const val RC_CHECK_IN = 1
    private const val RC_CHASTITY = 2
    private const val RC_TASK = 3

    fun scheduleAll(context: Context) {
        val st = Guardian.state.value
        scheduleNextCheckIn(context)
        st.chastity?.let { scheduleChastityEnd(context, it.endsAt) }
        st.proofs.filter { it.reason.penalized }.forEach { scheduleProofDeadline(context, it) }
        st.task?.let { scheduleTask(context, Guardian.nextTaskAlarm(it, Guardian.now())) }
    }

    /** Next check-in lands somewhere between 60% and 100% of the interval, so never later than it. */
    fun scheduleNextCheckIn(context: Context) {
        val minutes = Guardian.config.value.checkInMinutes.toLong()
        val delay = Random.nextLong((minutes * 6 / 10).coerceAtLeast(15), minutes + 1) * MINUTE
        val at = Guardian.now() + delay
        set(context, pending(context, ACTION_CHECK_IN, RC_CHECK_IN), at)
        Guardian.state.update { it.copy(nextCheckInAt = at) }
    }

    fun scheduleProofDeadline(context: Context, request: ProofRequest) {
        val at = max(request.dueAt, Guardian.now() + 5_000)
        set(context, pending(context, ACTION_PROOF_DEADLINE, requestCode(request), request.id), at)
    }

    fun cancelProofDeadline(context: Context, request: ProofRequest) =
        cancel(context, pending(context, ACTION_PROOF_DEADLINE, requestCode(request), request.id))

    fun scheduleChastityEnd(context: Context, at: Long) =
        set(context, pending(context, ACTION_CHASTITY_END, RC_CHASTITY), at)

    fun cancelChastityEnd(context: Context) =
        cancel(context, pending(context, ACTION_CHASTITY_END, RC_CHASTITY))

    fun scheduleTask(context: Context, at: Long) =
        set(context, pending(context, ACTION_TASK, RC_TASK), max(at, Guardian.now() + 5_000))

    fun cancelTask(context: Context) =
        cancel(context, pending(context, ACTION_TASK, RC_TASK))

    fun cancelAll(context: Context, proofs: List<ProofRequest>) {
        cancel(context, pending(context, ACTION_CHECK_IN, RC_CHECK_IN))
        cancelChastityEnd(context)
        cancelTask(context)
        proofs.forEach { cancelProofDeadline(context, it) }
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun requestCode(request: ProofRequest): Int = 1000 + (request.id % 1_000_000).toInt()

    private fun pending(context: Context, action: String, requestCode: Int, id: Long = 0): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun set(context: Context, pi: PendingIntent, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(context: Context, pi: PendingIntent) =
        context.getSystemService(AlarmManager::class.java).cancel(pi)
}
