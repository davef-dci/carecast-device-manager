package app.carecast.devicemanager

object Constants {
    const val FRAME_PACKAGE = "app.carecast.frame"
    const val FRAME_MAIN_ACTIVITY = "com.example.hotlaps.frameapp.MainActivity"
    // Frame's own hidden field-service gesture (long-press the version badge) launches
    // system Settings directly. Without this in the lock-task allowlist, that launch is
    // silently blocked by Android itself (ActivityManager error code 101) regardless of
    // Frame's code — confirmed 2026-08-22 on real hardware. Keep allowlisted so field
    // techs retain Wi-Fi/diagnostics access without needing a separate escape mechanism.
    const val SETTINGS_PACKAGE = "com.android.settings"
    const val TAG = "DeviceManager"
    const val RELAUNCH_INTERVAL_MS = 20_000L
}
