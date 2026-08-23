package app.carecast.devicemanager

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// Which Firebase project's manifest to poll is a build-flavor decision (prod ->
// carecast-v2, sandbox -> carecast-sandbox), same split frameapp already uses — not a
// hardcoded constant, so a sandbox-flavor build can never accidentally end up pointed
// at production or vice versa.
private val MANIFEST_URL =
    "https://firestore.googleapis.com/v1/projects/${BuildConfig.FIRESTORE_PROJECT_ID}/databases/(default)/documents/frameReleases/latest"

class ReleaseChecker(private val context: Context) {

    fun checkAndInstall() {
        try {
            val manifest = fetchManifest() ?: return
            Log.i(Constants.TAG, "Manifest: versionCode=${manifest.versionCode} halted=${manifest.halted}")

            if (manifest.halted) {
                Log.i(Constants.TAG, "Release halted, skipping")
                return
            }

            val installedVersionCode = installedVersionCode()
            if (manifest.versionCode <= installedVersionCode) {
                Log.i(Constants.TAG, "Up to date (installed=$installedVersionCode, available=${manifest.versionCode})")
                return
            }

            Log.i(Constants.TAG, "Update available: $installedVersionCode -> ${manifest.versionCode}, downloading")
            val apkFile = File(context.filesDir, "pending_update.apk")
            downloadTo(manifest.downloadUrl, apkFile)
            Log.i(Constants.TAG, "Downloaded ${apkFile.length()} bytes")

            if (!verifyChecksum(apkFile, manifest.sha256)) {
                Log.e(Constants.TAG, "Checksum mismatch, aborting install")
                apkFile.delete()
                return
            }
            Log.i(Constants.TAG, "Checksum verified")

            val archiveInfo = context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath, GET_SIGNING_CERTIFICATES
            )
            if (archiveInfo == null) {
                Log.e(Constants.TAG, "Could not parse downloaded APK, aborting")
                apkFile.delete()
                return
            }
            if (archiveInfo.packageName != manifest.applicationId) {
                Log.e(Constants.TAG, "applicationId mismatch: apk=${archiveInfo.packageName} manifest=${manifest.applicationId}, aborting")
                apkFile.delete()
                return
            }
            val apkVersionCode = if (Build.VERSION.SDK_INT >= 28) archiveInfo.longVersionCode else archiveInfo.versionCode.toLong()
            if (apkVersionCode != manifest.versionCode.toLong()) {
                Log.e(Constants.TAG, "versionCode mismatch: apk=$apkVersionCode manifest=${manifest.versionCode}, aborting")
                apkFile.delete()
                return
            }
            val apkCertSha256 = signingCertSha256(archiveInfo)
            if (apkCertSha256 == null || !apkCertSha256.equals(manifest.signingCertSha256, ignoreCase = true)) {
                Log.e(Constants.TAG, "Signing cert mismatch: apk=$apkCertSha256 manifest=${manifest.signingCertSha256}, aborting")
                apkFile.delete()
                return
            }
            Log.i(Constants.TAG, "applicationId/versionCode/signing cert all verified against manifest claims")

            installSilently(apkFile)
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Update check failed", e)
        }
    }

    private fun installedVersionCode(): Long {
        return try {
            val info = context.packageManager.getPackageInfo(Constants.FRAME_PACKAGE, 0)
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        } catch (e: PackageManager.NameNotFoundException) {
            0L
        }
    }

    private data class Manifest(
        val versionCode: Long,
        val applicationId: String,
        val downloadUrl: String,
        val sha256: String,
        val signingCertSha256: String,
        val halted: Boolean
    )

    private fun fetchManifest(): Manifest? {
        val conn = URL(MANIFEST_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) {
                Log.e(Constants.TAG, "Manifest fetch failed: HTTP ${conn.responseCode}")
                return null
            }
            val body = conn.inputStream.bufferedReader().readText()
            val fields = JSONObject(body).getJSONObject("fields")
            fun str(key: String) = fields.getJSONObject(key).getString("stringValue")
            fun bool(key: String) = fields.optJSONObject(key)?.optBoolean("booleanValue", false) ?: false
            return Manifest(
                versionCode = fields.getJSONObject("versionCode").getString("integerValue").toLong(),
                applicationId = str("applicationId"),
                downloadUrl = str("downloadUrl"),
                sha256 = str("sha256"),
                signingCertSha256 = str("signingCertSha256"),
                halted = bool("halted")
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadTo(url: String, dest: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    private fun signingCertSha256(info: android.content.pm.PackageInfo): String? {
        val signers = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        val cert = signers?.firstOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun installSilently(apkFile: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        session.openWrite("update", 0, apkFile.length()).use { out ->
            apkFile.inputStream().use { it.copyTo(out) }
            session.fsync(out)
        }
        val resultIntent = android.content.Intent(context, InstallResultReceiver::class.java)
        resultIntent.setPackage(context.packageName)
        val pending = android.app.PendingIntent.getBroadcast(
            context, sessionId, resultIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        )
        Log.i(Constants.TAG, "Committing silent install session $sessionId")
        session.commit(pending.intentSender)
        session.close()
    }
}
