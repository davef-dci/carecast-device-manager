package app.carecast.devicemanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import java.util.concurrent.Executors

/**
 * No lock task / kiosk enforcement here, deliberately. Earlier revisions locked Frame
 * into lock task mode for theft/repurposing protection, but that's not a real threat in
 * this deployment context (monitored senior-living rooms) — and it caused a real bench
 * lockout (lock task blocked uninstall, Settings access, even wireless debugging itself,
 * with no software escape short of clearing Device Owner status entirely). Device Owner
 * status is kept (needed for silent PackageInstaller updates), but nothing here ever
 * restricts navigation: Home, Settings, everything stays normally reachable, always.
 */
class WatchdogService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val updateExecutor = Executors.newSingleThreadExecutor()
    private lateinit var releaseChecker: ReleaseChecker

    private val relaunchRunnable = object : Runnable {
        override fun run() {
            relaunchFrameIfNotRunning()
            recordCheck()
            handler.postDelayed(this, Constants.RELAUNCH_INTERVAL_MS)
        }
    }

    private val updateCheckRunnable = object : Runnable {
        override fun run() {
            updateExecutor.submit { releaseChecker.checkAndInstall() }
            val jitter = (0 until Constants.UPDATE_CHECK_JITTER_MS).random()
            handler.postDelayed(this, Constants.UPDATE_CHECK_INTERVAL_MS + jitter)
        }
    }

    override fun onCreate() {
        super.onCreate()
        releaseChecker = ReleaseChecker(this)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(relaunchRunnable)
        handler.post(relaunchRunnable)
        handler.removeCallbacks(updateCheckRunnable)
        handler.postDelayed(updateCheckRunnable, Constants.UPDATE_CHECK_INTERVAL_MS)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(relaunchRunnable)
        handler.removeCallbacks(updateCheckRunnable)
        super.onDestroy()
    }

    private fun relaunchFrameIfNotRunning() {
        // Deliberately NOT trying to detect "is Frame's process actually alive" —
        // ActivityManager.getRunningAppProcesses() is restricted to the caller's own
        // process for most apps, and empirically (tested live 2026-08-22) that
        // restriction is NOT waived for this Device Owner app on this OS build either:
        // a version of this method that checked process state via that API still
        // relaunched Frame over Settings, because the check silently always came back
        // "not running." Rather than keep guessing at an unreliable, permission-gated
        // API, this just relaunches unconditionally on a long interval. Plain
        // reorder-to-front (harmless no-op if Frame's already there), infrequent enough
        // that real field-service work in Settings has time to finish uninterrupted.
        val intent = Intent().apply {
            component = ComponentName(Constants.FRAME_PACKAGE, Constants.FRAME_MAIN_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        try {
            startActivity(intent)
            Log.i(Constants.TAG, "Frame relaunch issued")
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
