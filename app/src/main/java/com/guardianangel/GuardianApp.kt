package com.guardianangel

import android.app.Application
import com.guardianangel.core.Guardian

class GuardianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Guardian.init(this)
    }
}
