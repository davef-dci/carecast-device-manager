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

            // A fresh install (no prior version to replace) never fires
            // MY_PACKAGE_REPLACED for Frame, so nothing else launches it — confirmed
            // live 2026-08-22, Frame sat on the Android launcher after a successful
            // silent install until manually started. An in-place update DOES fire that
            // broadcast (Frame's own boot receiver picks it up), so this is technically
            // redundant in that case, but harmless — relaunching an already-foreground
            // Frame is just a no-op reorder-to-front.
            val launch = Intent().apply {
                component = android.content.ComponentName(Constants.FRAME_PACKAGE, Constants.FRAME_MAIN_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            try {
                context.startActivity(launch)
                Log.i(Constants.TAG, "Frame launched after successful install")
            } catch (e: Exception) {
                Log.e(Constants.TAG, "Failed to launch Frame after install", e)
            }
        } else {
            context.getSharedPreferences("watchdog", Context.MODE_PRIVATE)
                .edit()
                .putString("last_update_result", "failed: $status $message")
                .apply()
        }
    }
}
