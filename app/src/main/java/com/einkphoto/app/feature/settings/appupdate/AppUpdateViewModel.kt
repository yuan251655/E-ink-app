package com.einkphoto.app.feature.settings.appupdate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class AppUpdatePhase { Idle, Checking, UpToDate, UpdateAvailable, Downloading, ReadyToInstall, Failed }

data class AppUpdateUiState(
    val currentVersionName: String,
    val currentVersionCode: Long,
    val phase: AppUpdatePhase = AppUpdatePhase.Idle,
    val release: AppRelease? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String? = null,
)

class AppUpdateViewModel(private val context: Context) : ViewModel() {
    private val repository = AppUpdateRepository(context.applicationContext)
    private val mutableState = MutableStateFlow(currentState())
    val state = mutableState.asStateFlow()
    private var readyApk: File? = null

    fun check() = viewModelScope.launch {
        mutableState.value = currentState().copy(phase = AppUpdatePhase.Checking, message = "正在检查更新…")
        repository.fetchRelease().onSuccess { release ->
            val current = currentState()
            val minimumRequired = release.minSupportedVersionCode > current.currentVersionCode
            val newer = release.versionCode > current.currentVersionCode
            mutableState.value = when {
                newer || minimumRequired -> current.copy(
                    phase = AppUpdatePhase.UpdateAvailable,
                    release = release,
                    message = if (minimumRequired || release.forceUpdate) "发现需要更新的版本" else "发现新版本",
                )
                else -> current.copy(phase = AppUpdatePhase.UpToDate, release = release, message = "当前已是最新版本")
            }
        }.onFailure { error ->
            mutableState.value = currentState().copy(phase = AppUpdatePhase.Failed, message = checkErrorMessage(error.message))
        }
    }

    fun download() {
        val release = mutableState.value.release ?: return
        if (mutableState.value.phase == AppUpdatePhase.Downloading) return
        viewModelScope.launch {
            readyApk = null
            mutableState.value = mutableState.value.copy(phase = AppUpdatePhase.Downloading, downloadedBytes = 0, totalBytes = release.sizeBytes, message = "正在下载更新包…")
            repository.download(release) { downloaded, total ->
                mutableState.value = mutableState.value.copy(downloadedBytes = downloaded, totalBytes = total)
            }.onSuccess { apk ->
                readyApk = apk
                mutableState.value = mutableState.value.copy(phase = AppUpdatePhase.ReadyToInstall, message = "更新包已校验完成，可开始安装")
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(phase = AppUpdatePhase.Failed, message = downloadErrorMessage(error.message))
            }
        }
    }

    fun install(): Intent? {
        val apk = readyApk ?: return null
        return repository.createInstallIntent(apk)
    }

    fun unknownSourcesSettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    private fun currentState(): AppUpdateUiState {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return AppUpdateUiState(
            currentVersionName = info.versionName ?: "未知版本",
            currentVersionCode = PackageInfoCompat.getLongVersionCode(info),
        )
    }

    private fun checkErrorMessage(code: String?): String = when {
        code == "invalid_update_url" -> "更新地址不在可信服务器范围内，已停止下载"
        code == "http_404" || code?.contains("404") == true -> "暂未发布线上更新版本，发布 Release 后即可检查更新"
        code == "http_401" || code == "http_403" || code?.contains("401") == true || code?.contains("403") == true -> "无权读取更新信息，请检查远程发布权限"
        code in setOf("unsupported_channel", "package_mismatch", "invalid_version", "invalid_apk_url", "invalid_checksum", "invalid_size") -> "远程更新信息格式无效，未执行下载"
        code?.contains("Unable to resolve host", ignoreCase = true) == true -> "无法连接更新服务器，请检查手机互联网连接"
        code?.contains("timeout", ignoreCase = true) == true -> "连接更新服务器超时，请稍后重试"
        else -> "检查更新失败，请检查手机互联网后重试"
    }

    private fun downloadErrorMessage(code: String?): String = when (code) {
        "checksum_mismatch" -> "更新包校验失败，已删除该文件，请重新下载"
        "apk_package_mismatch" -> "更新包身份不匹配，已阻止安装"
        "apk_signature_mismatch" -> "更新包签名与当前 App 不一致，已阻止安装"
        "invalid_apk" -> "更新包格式无效，已阻止安装"
        "download_incomplete" -> "下载未完成，请检查网络后重试"
        "http_404" -> "未找到远程 APK，请检查发布版本"
        else -> "下载更新包失败，请稍后重试"
    }
}
