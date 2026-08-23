package app.carecast.devicemanager

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Emergency-only escape hatch, added live during bench testing after discovering that
 * Android (correctly, by design) blocks every external attempt to disable/stop/uninstall
 * a Device Owner app via shell — force-stop, pm disable-user, and DELETE_FAILED_APP_PINNED
 * on uninstall all failed. Only the app itself can self-clear Device Owner status.
 *
 * This is intentionally crude (unauthenticated local broadcast) because the goal right
 * now is recovering a bricked-feeling bench device, not a production-grade feature.
 * Should be removed or properly access-controlled before this code is ever considered
 * for a real device, since anyone with adb shell access could otherwise strip Device
 * Owner status at will.
 */
class EmergencyReleaseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(Constants.TAG, "EmergencyReleaseReceiver triggered")
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            dpm.clearDeviceOwnerApp(context.packageName)
            Log.i(Constants.TAG, "Device owner status cleared")
        } else {
            Log.i(Constants.TAG, "Not device owner, nothing to clear")
        }
    }
}
