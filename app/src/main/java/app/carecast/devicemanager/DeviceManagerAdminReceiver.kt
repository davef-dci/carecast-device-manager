package app.carecast.devicemanager

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DeviceManagerAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(Constants.TAG, "Device admin enabled")
        context.startForegroundService(Intent(context, WatchdogService::class.java))
    }
}
