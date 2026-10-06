package com.guardianangel.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.guardianangel.core.Guardian
import com.guardianangel.core.Line

/**
 * Lock guard: as a device admin with no policies, Android won't uninstall her until her admin is
 * removed first. During a lock, Lock guard blocks that screen too. She warns you when you try.
 */
class GuardAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Guardian.init(context)
        return Guardian.line(Line.ADMIN_OFF)
    }
}
