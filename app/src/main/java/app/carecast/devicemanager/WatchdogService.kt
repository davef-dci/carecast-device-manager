package app.carecast.devicemanager

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WatchdogService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName
    private var lockTaskApplied = false

    private val relaunchRunnable = object : Runnable {
        override fun run() {
            relaunchFrame()
            recordCheck()
            handler.postDelayed(this, Constants.RELAUNCH_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        admin = ComponentName(this, DeviceManagerAdminReceiver::class.java)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applyLockTaskAllowlist()
        handler.removeCallbacks(relaunchRunnable)
        handler.post(relaunchRunnable)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(relaunchRunnable)
        super.onDestroy()
    }

    private fun applyLockTaskAllowlist() {
        val isOwner = dpm.isDeviceOwnerApp(packageName)
        Log.i(Constants.TAG, "isDeviceOwnerApp=$isOwner")
        if (isOwner && !lockTaskApplied) {
            val allowlist = arrayOf(packageName, Constants.FRAME_PACKAGE, Constants.SETTINGS_PACKAGE)
            dpm.setLockTaskPackages(admin, allowlist)
            lockTaskApplied = true
            Log.i(Constants.TAG, "Lock task allowlist set: ${allowlist.joinToString()}")
        }
    }

    private fun relaunchFrame() {
        // FLAG_ACTIVITY_REORDER_TO_FRONT reuses Frame's existing task if it's still
        // running, and ActivityOptions.setLockTaskEnabled only takes effect on a
        // genuinely fresh task launch — reordering an existing task to front silently
        // does NOT (re-)engage lock task. So: only do the cheap reorder-to-front when
        // we're already locked; otherwise force a fresh task (CLEAR_TASK) so lock task
        // actually (re-)activates, whether Frame crashed or simply was never locked yet.
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val alreadyLocked = am.lockTaskModeState == android.app.ActivityManager.LOCK_TASK_MODE_LOCKED

        val intent = Intent().apply {
            component = ComponentName(Constants.FRAME_PACKAGE, Constants.FRAME_MAIN_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (alreadyLocked) {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            } else {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
        try {
            if (!alreadyLocked && Build.VERSION.SDK_INT >= 28 && dpm.isDeviceOwnerApp(packageName)) {
                val options = ActivityOptions.makeBasic()
                options.setLockTaskEnabled(true)
                startActivity(intent, options.toBundle())
            } else {
                startActivity(intent)
            }
            Log.i(Constants.TAG, "Frame relaunch issued (alreadyLocked=$alreadyLocked)")
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Failed to relaunch Frame", e)
        }
    }

    private fun recordCheck() {
        val prefs = getSharedPreferences("watchdog", Context.MODE_PRIVATE)
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        prefs.edit().putString("last_check", ts).putInt("check_count", prefs.getInt("check_count", 0) + 1).apply()
    }

    private fun buildNotification(): android.app.Notification {
        val channelId = "device_manager_watchdog"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Device Manager", NotificationManager.IMPORTANCE_MIN)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("CareCast Device Manager")
            .setContentText("Watching CareCast Frame")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}
