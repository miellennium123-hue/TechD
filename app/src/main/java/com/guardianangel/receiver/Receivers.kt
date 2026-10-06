package com.guardianangel.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardianangel.core.Guardian
import com.guardianangel.core.Scheduler

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Guardian.init(context)
        when (intent.action) {
            Scheduler.ACTION_CHECK_IN -> Guardian.onCheckIn()
            Scheduler.ACTION_PROOF_DEADLINE -> Guardian.onProofDeadline(intent.getLongExtra(Scheduler.EXTRA_ID, -1))
            Scheduler.ACTION_CHASTITY_END -> Guardian.onChastityEnd()
        }
    }
}

/** Alarms don't survive reboots or app updates, so put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Guardian.init(context)
        if (Guardian.config.value.enabled) Scheduler.scheduleAll(context)
    }
}
