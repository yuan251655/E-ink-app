package com.einkphoto.app.feature.settings.appupdate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.einkphoto.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

data class AppRelease(
    val channel: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val releaseNotes: String,
    val forceUpdate: Boolean,
    val minSupportedVersionCode: Long,
)

class AppUpdateRepository(private val context: Context) {
    suspend fun fetchRelease(): Result<AppRelease> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openUpdateConnection(BuildConfig.APP_UPDATE_MANIFEST_URL)
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            parseRelease(JSONObject(payload))
        }
    }

    suspend fun download(release: AppRelease, onProgress: (downloaded: Long, total: Long) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = openUpdateConnection(release.apkUrl)
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
                val directory = File(context.getExternalFilesDir("Download"), "updates").apply { mkdirs() }
                val target = File(directory, "eink-photo-${release.versionCode}.apk")
                val temporary = File(directory, ".${target.name}.downloading")
                target.delete()
                temporary.delete()
                val digest = MessageDigest.getInstance("SHA-256")
                connection.inputStream.use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            downloaded += count
                            onProgress(downloaded, total)
                        }
                        output.fd.sync()
                        if (total > 0 && downloaded != total) error("download_incomplete")
                    }
                }
                connection.disconnect()
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(release.sha256, ignoreCase = true)) {
                    temporary.delete()
                    error("checksum_mismatch")
                }
                runCatching { verifyArchiveIdentity(temporary) }
                    .onFailure { temporary.delete() }
                    .getOrThrow()
                check(temporary.renameTo(target)) { "download_commit_failed" }
                target
            }
        }

    fun createInstallIntent(apk: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun parseRelease(input: JSONObject): AppRelease {
        val channel = input.optString("channel").trim()
        val packageName = input.optString("package_name").trim()
        val versionName = input.optString("version_name").trim()
        val versionCode = input.optLong("version_code", -1L)
        val apkUrl = input.optString("apk_url").trim()
        val sha256 = input.optString("sha256").trim().lowercase()
        val sizeBytes = input.optLong("size_bytes", -1L)
        val minimum = input.optLong("min_supported_version_code", 1L)
        require(channel == "stable") { "unsupported_channel" }
        require(packageName == context.packageName) { "package_mismatch" }
        require(versionName.isNotBlank() && versionCode > 0) { "invalid_version" }
        require(isTrustedUpdateUrl(apkUrl)) { "invalid_apk_url" }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "invalid_checksum" }
        require(sizeBytes > 0) { "invalid_size" }
        return AppRelease(
            channel = channel,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            apkUrl = apkUrl,
            sha256 = sha256,
            sizeBytes = sizeBytes,
            releaseNotes = input.optString("release_notes").trim(),
            forceUpdate = input.optBoolean("force_update", false),
            minSupportedVersionCode = minimum,
        )
    }

    private fun openUpdateConnection(url: String): HttpURLConnection {
        require(isTrustedUpdateUrl(url)) { "invalid_update_url" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, application/vnd.android.package-archive, */*")
            connect()
            if (responseCode !in 200..299) {
                disconnect()
                error("http_$responseCode")
            }
        }
    }

    private fun verifyArchiveIdentity(apk: File) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("invalid_apk")
        require(archive.packageName == context.packageName) { "apk_package_mismatch" }
        require(signatureDigests(archive) == signatureDigests(installed)) { "apk_signature_mismatch" }
    }

    @Suppress("DEPRECATION")
    private fun signatureDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            info.signatures.orEmpty()
        }
        return signatures.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}

internal fun isTrustedUpdateUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    if (uri.userInfo != null || uri.host.isNullOrBlank() || uri.fragment != null) return@runCatching false
    if (uri.scheme.equals("https", ignoreCase = true)) return@runCatching true
    uri.scheme.equals("http", ignoreCase = true) &&
        uri.host == "107.173.157.41" &&
        uri.port == 3000 &&
        (uri.path == "/yj/E-ink-APP/raw/branch/main/release-manifest/stable.json" ||
            uri.path.startsWith("/yj/E-ink-APP/releases/download/"))
}.getOrDefault(false)
