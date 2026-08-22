package app.carecast.devicemanager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startForegroundService(Intent(this, WatchdogService::class.java))

        val statusText = findViewById<TextView>(R.id.statusText)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isOwner = dpm.isDeviceOwnerApp(packageName)
        val prefs = getSharedPreferences("watchdog", Context.MODE_PRIVATE)

        statusText.text = buildString {
            appendLine("isDeviceOwnerApp: $isOwner")
            appendLine("Frame package: ${Constants.FRAME_PACKAGE}")
            appendLine("Last watchdog check: ${prefs.getString("last_check", "(none yet)")}")
            appendLine("Check count: ${prefs.getInt("check_count", 0)}")
        }
    }
}
