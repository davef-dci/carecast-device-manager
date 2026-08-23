package app.carecast.devicemanager

object Constants {
    const val FRAME_PACKAGE = "app.carecast.frame"
    const val FRAME_MAIN_ACTIVITY = "com.example.hotlaps.frameapp.MainActivity"
    const val TAG = "DeviceManager"
    // Relaunches unconditionally (see WatchdogService), so this is a direct trade-off
    // between crash-recovery speed and how long a legitimate Settings/field-service
    // session gets before a possible interruption. 5 minutes errs toward not disrupting
    // real work; revisit once there's a reliable way to detect Frame's actual state.
    const val RELAUNCH_INTERVAL_MS = 300_000L
    // Test-only cadence for live verification. Production should be much less
    // frequent (~hourly + random jitter, per the architecture assessment) once this
    // moves off the sandbox manifest and onto a real per-environment config.
    const val UPDATE_CHECK_INTERVAL_MS = 45_000L
}
