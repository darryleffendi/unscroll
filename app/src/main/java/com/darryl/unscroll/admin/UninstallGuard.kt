package com.darryl.unscroll.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.darryl.unscroll.R

/**
 * Optional device admin with no policies. Its only effect: while active, Android won't
 * uninstall unscroll until admin is turned off in Settings › Security › Device admin apps.
 * Friction, not a lock: it never blocks anything else.
 */
class UninstallGuard : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)

    companion object {
        fun component(context: Context) = ComponentName(context, UninstallGuard::class.java)

        fun isActive(context: Context): Boolean =
            context.getSystemService(DevicePolicyManager::class.java).isAdminActive(component(context))

        fun enableIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.admin_explanation))

        fun deactivate(context: Context) {
            context.getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(component(context))
        }
    }
}
