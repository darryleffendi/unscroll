package com.darryl.unscroll

import android.app.Application
import com.darryl.unscroll.debug.CrashLog

class UnscrollApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
