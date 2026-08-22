package app.carecast.devicemanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(Constants.TAG, "BootReceiver fired: ${intent.action}")
        context.startForegroundService(Intent(context, WatchdogService::class.java))
    }
}
