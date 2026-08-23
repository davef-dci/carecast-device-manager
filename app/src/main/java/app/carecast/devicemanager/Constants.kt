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
    // Hourly, plus random jitter applied in WatchdogService (see
    // UPDATE_CHECK_JITTER_MS) — avoids every device in the fleet hammering the manifest
    // at the exact same moment. 45s was tonight's live-testing cadence, not a real one.
    const val UPDATE_CHECK_INTERVAL_MS = 3_600_000L
    const val UPDATE_CHECK_JITTER_MS = 300_000L
}
