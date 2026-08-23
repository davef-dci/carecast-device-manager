package app.carecast.devicemanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(Constants.TAG, "Install result: status=$status message=$message")

        if (status == PackageInstaller.STATUS_SUCCESS) {
            Log.i(Constants.TAG, "Update installed successfully")
            context.getSharedPreferences("watchdog", Context.MODE_PRIVATE)
                .edit()
                .putString("last_update_result", "success")
                .apply()
        } else {
            context.getSharedPreferences("watchdog", Context.MODE_PRIVATE)
                .edit()
                .putString("last_update_result", "failed: $status $message")
                .apply()
        }
    }
}
