# CareCast Frame Provisioning Runbook

How to take a brand-new (or factory-reset) Android tablet from nothing to a fully
provisioned, resident-assigned CareCast Frame with remote-update capability. Written
2026-08-23 after the first end-to-end build-and-test pass — see the `project-carecast-
device-manager-ota` memory for the full story behind each of these steps, including
three real incidents that shaped this process.

## Before you start

- A factory-reset or never-configured tablet. Device Owner provisioning requires **no
  existing accounts** on the device — this only works cleanly on a genuinely fresh unit.
- **A USB cable. Use USB, not wireless debugging, for the entire provisioning process.**
  Wireless debugging proved unreliable during testing — it drops unpredictably, and if
  anything ever goes wrong with Device Owner policy on the device, wireless debugging
  can become completely unrecoverable (a real incident: it refused to stay enabled at
  all while a bad policy was active, with no way back in except USB). USB debugging is
  a separate toggle that wasn't affected by the same failure.
- Signed release APKs for both apps, built from the `prod` flavor:
  - `app-prod-release.apk` for **CareCast Frame** (from `frameapp`, `main` branch)
  - `app-prod-release.apk` for **CareCast Device Manager** (from `carecast-device-manager`, `main`)
  - Verify signatures before installing anything (see `frameapp/keystore.properties` for
    local signing; production cert SHA-256 should be
    `41:A6:03:60:F4:10:07:29:0A:44:5A:39:24:31:F1:4A:65:6F:62:C6:A9:D3:0A:53:8B:EE:05:A7:9B:ED:35:A6`)
- `adb` on PATH, `gcloud`/`firebase` CLI authenticated if you'll be publishing a release
  as part of this session too.

## Step 1 — Basic device setup

1. Power on the tablet, get through initial Android setup **without** signing into any
   Google account (skip that step — Device Owner provisioning requires zero accounts).
2. Settings → About tablet → tap "Build number" 7 times to unlock Developer options.
3. Settings → System → Developer options → enable **USB debugging**.
4. Connect via USB. Accept the "Allow USB debugging?" prompt on the tablet, check
   "Always allow from this computer."
5. Confirm connection:
   ```powershell
   adb devices
   ```
   Should show exactly one device, status `device` (not `unauthorized` or `offline`).

## Step 2 — Verify the device is genuinely clean

```powershell
adb shell dumpsys device_policy | Select-String "Device Owner" -Context 0,2
adb shell dumpsys account | Select-String "Account \{"
```
Both should return **nothing**. If either shows existing state, this isn't a clean
device — factory reset it and start over rather than trying to provision on top of
existing state.

Worth recording for your own records while you're here:
```powershell
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell pm list packages | Select-String "com.google.android.gms|com.android.vending"
```

## Step 3 — Install and provision Device Manager

```powershell
adb install app-prod-release.apk    # the Device Manager APK
adb shell dpm set-device-owner app.carecast.devicemanager/.DeviceManagerAdminReceiver
```
Should print `Success: Device owner set to ...`. Confirm:
```powershell
adb shell dumpsys device_policy | Select-String "Device Owner" -Context 0,2
```

Device Manager's boot receiver and watchdog service start automatically once it's
Device Owner — no further action needed to activate it.

## Step 4 — Install CareCast Frame

```powershell
adb install app-prod-release.apk    # the Frame APK
```
Device Manager's watchdog will launch it automatically within a few minutes, or force
it immediately:
```powershell
adb shell am start -n app.carecast.frame/com.example.hotlaps.frameapp.MainActivity
```

## Step 5 — Get the pairing code and assign the resident

Frame self-registers to Firestore on first launch — no admin action needed for that
part. Find the pairing code either on-device (shown in the UI) or in **Admin → Frames**
in the webapp — the new device will show up unassigned, identifiable by its reported
manufacturer/model/Android version/app version. Assign it to the correct resident there.

## Step 6 — Local smoke test (do this before the physical swap, not after)

- [ ] Content loads correctly for the assigned resident (photos, schedule, reminders if enabled)
- [ ] Home button and Settings remain freely accessible — no lock task, nothing should
      block navigation. (If anything *does* block navigation, stop and investigate
      before going further — that's not expected behavior anymore.)
- [ ] Crash recovery: `adb shell am force-stop app.carecast.frame`, confirm Device
      Manager relaunches it within ~5 minutes (or check `adb logcat -s DeviceManager:I`
      for the relaunch line if you don't want to wait)
- [ ] Reboot recovery: power-cycle the tablet, confirm Frame is back on screen within
      ~10-30 seconds (this is Frame's own boot receiver, independent of Device Manager)
- [ ] Update check isn't erroring: `adb logcat -s DeviceManager:I` briefly, look for a
      clean `Up to date` or successful install line, not repeated failures

## Step 7 — Physical swap

Visit the resident, swap the new frame in for the old one, remove the old frame.

## Step 8 — Post-swap check

Confirm the new frame is showing the right resident's content in the room. The old
frame's stale registration in Firestore can be left alone or deleted from Admin →
Frames — deleting it doesn't affect the new frame, and per the admin UI's own warning,
only matters if the old device is still somehow powered on and running.

---

## Known gotchas — read before you hit them, not after

- **Signature mismatch blocks in-place updates.** If a device already has *any* version
  of Frame or Device Manager installed under a different signing key, `adb install -r`
  will fail (or a future silent update will fail) with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
  The only fix is a full uninstall + fresh install, which wipes local app data —
  meaning Frame's device ID resets and it re-registers as a new, unassigned frame. This
  is expected and fine for the one-time transition of an old pre-signing-pipeline
  device; it should never happen again once a device is on the current key.
- **Check which branch a release was actually built from before publishing it.** A
  higher `versionCode` does not guarantee newer code — this bit us once already (a
  build from `main` silently lacked a feature present on `feature/reminders`, even
  though its version number was higher).
- **`pm disable-user` and `force-stop` do not work on the Device Manager app once it's
  Device Owner** — Android deliberately protects it ("Cannot disable a protected
  package"). If you ever need to remove Device Owner status without a factory reset,
  it has to come from inside the app's own code (self-clear), not from adb shell.
- **Do not re-add lock task / kiosk enforcement** without deliberately building and
  testing an escape hatch *first*. An earlier version of Device Manager did this and it
  caused a near-total lockout — blocked uninstall, blocked Settings, and even blocked
  wireless debugging itself from staying enabled. See the OTA project memory for the
  full incident if this ever comes up again.
- **`adb devices` may show the same tablet twice** if both USB and wireless debugging
  are active — target the right one explicitly with `-s <serial>`.

## Publishing a new release (Admin UI)

`care-cast-webapp` has a **Frame Releases** admin tab (Admin → Frame Releases) as of
2026-08-23. This is now the normal way to publish — it handles history, targeting
specific devices instead of the whole fleet, and halting, none of which the manual
fallback below does on its own. What it does **not** do is build/sign the APK or
upload it to Storage — those two steps stay manual regardless of which path you use.

1. **Build and sign**, same as always:
   ```powershell
   # bump versionCode/versionName in frameapp/app/build.gradle.kts first
   .\gradlew.bat :app:assembleProdRelease
   ```
2. **Upload the APK to Storage** with a fresh download token (still a script step — the
   UI intentionally doesn't handle file upload, to avoid needing browser-based Storage
   upload infrastructure at this scale):
   ```powershell
   $apk = "app\build\outputs\apk\prod\release\app-prod-release.apk"
   $token = [guid]::NewGuid().ToString()
   gcloud storage cp $apk "gs://carecast-v2.firebasestorage.app/frameReleases/carecast-frame-<version>.apk"
   gcloud storage objects update "gs://carecast-v2.firebasestorage.app/frameReleases/carecast-frame-<version>.apk" --custom-metadata=firebaseStorageDownloadTokens=$token
   $downloadUrl = "https://firebasestorage.googleapis.com/v0/b/carecast-v2.firebasestorage.app/o/frameReleases%2Fcarecast-frame-<version>.apk?alt=media&token=$token"
   $downloadUrl   # copy this into the UI form
   ```
3. In the webapp, **Admin → Frame Releases**: fill in version code/name, paste the
   download URL, and select the same local APK file in the file picker — the browser
   hashes it locally (Web Crypto, nothing uploaded) and fills in the SHA-256 for you.
   Signing cert SHA-256 is pre-filled with the current production key's value; only
   change it if the key itself has been rotated. Optionally list specific device IDs to
   target a canary rollout instead of the whole fleet. **Save as candidate** — this
   registers it but does not make it live.
4. Review the candidate in the release history table, then click **Publish** when
   ready. This is the point it actually becomes what Device Manager will detect and
   install.
5. If something's wrong after publishing, click **Halt** on the live release banner —
   stops new devices from picking it up. Devices that already installed it need a
   forward-fix release (higher versionCode), not a downgrade — same rule as always.

## Manual fallback (direct Firestore/Storage access)

Useful if the webapp/API isn't reachable, or for scripting/automation later. Same
underlying data the UI writes to, just via `gcloud`/`curl` directly. From `frameapp` on
`main`:

```powershell
# 1. Bump versionCode/versionName in app/build.gradle.kts, then build:
.\gradlew.bat :app:assembleProdRelease

# 2. Checksum the output:
$apk = "app\build\outputs\apk\prod\release\app-prod-release.apk"
$hash = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()

# 3. Upload to production Storage with a fresh download token:
$token = [guid]::NewGuid().ToString()
gcloud storage cp $apk "gs://carecast-v2.firebasestorage.app/frameReleases/carecast-frame-<version>.apk"
gcloud storage objects update "gs://carecast-v2.firebasestorage.app/frameReleases/carecast-frame-<version>.apk" --custom-metadata=firebaseStorageDownloadTokens=$token

# 4. Publish the manifest directly (adjust versionCode/versionName/sha256/downloadUrl)
#    — this bypasses the release-history collection the UI maintains, writing straight
#    to the live pointer doc Device Manager polls:
$accessToken = gcloud auth print-access-token
$downloadUrl = "https://firebasestorage.googleapis.com/v0/b/carecast-v2.firebasestorage.app/o/frameReleases%2Fcarecast-frame-<version>.apk?alt=media&token=$token"
$body = @{
  fields = @{
    versionCode = @{ integerValue = "<N>" }
    versionName = @{ stringValue = "<name>" }
    applicationId = @{ stringValue = "app.carecast.frame" }
    downloadUrl = @{ stringValue = $downloadUrl }
    sha256 = @{ stringValue = $hash }
    signingCertSha256 = @{ stringValue = "41a60360f41007290a445a392431f14a656f62c6a9d30a538bee05a79bed35a6" }
    mandatory = @{ booleanValue = $false }
    halted = @{ booleanValue = $false }
    releaseDate = @{ stringValue = (Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ") }
  }
} | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Patch -Uri "https://firestore.googleapis.com/v1/projects/carecast-v2/databases/(default)/documents/frameReleases/latest" -Headers @{Authorization="Bearer $accessToken"} -ContentType "application/json" -Body $body
```

To halt a bad release (stops new devices from picking it up; devices that already
installed it need a forward-fix release, not a downgrade):
```powershell
$accessToken = gcloud auth print-access-token
Invoke-RestMethod -Method Patch -Uri "https://firestore.googleapis.com/v1/projects/carecast-v2/databases/(default)/documents/frameReleases/latest?updateMask.fieldPaths=halted" -Headers @{Authorization="Bearer $accessToken"} -ContentType "application/json" -Body '{"fields":{"halted":{"booleanValue":true}}}'
```
