package com.darryl.unscroll.debug

import android.content.Context
import android.os.Build
import com.darryl.unscroll.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last error on the device (no network, nothing sent) so it can be shown in
 * the settings screen and copied by hand. That's the only way to diagnose a phone that
 * can't be reached with adb.
 */
object CrashLog {
    private const val FILE = "last_error.txt"
    @Volatile private var recordedThisProcess = false

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, "crash in ${thread.name}", error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** A caught error; only the first per process is written, to avoid churn in an event loop. */
    fun record(context: Context, where: String, error: Throwable) {
        if (recordedThisProcess) return
        recordedThisProcess = true
        runCatching { write(context.applicationContext, where, error) }
    }

    fun read(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    private fun write(context: Context, where: String, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        File(context.filesDir, FILE).writeText(
            "unscroll ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · $time\n" +
                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "$where\n\n$trace",
        )
    }
}
