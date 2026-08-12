package com.einkphoto.app.feature.settings.audio

import com.einkphoto.app.core.device.DevelopmentApHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class LanAudioRepository(
    private val client: DevelopmentApHttpClient = DevelopmentApHttpClient(),
) : AudioRepository {
    private val mutableSnapshot = MutableStateFlow(AudioSnapshot())
    override val snapshot: StateFlow<AudioSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun refresh(): AudioActionResult = client.get(STATUS_PATH).fold(
        onSuccess = { root ->
            parse(root.optJSONObject("data"))?.let {
                mutableSnapshot.value = it.copy(connected = true)
                AudioActionResult.Accepted
            } ?: reject("invalid_audio_status", "相框返回的音频状态不完整")
        },
        onFailure = { reject("device_unreachable", "无法读取音量，请确认相框已连接") },
    )

    override suspend fun save(masterVolume: Int, muted: Boolean): AudioActionResult {
        if (masterVolume !in 1..100) return reject("invalid_audio_config", "音量必须在 1 到 100 之间")
        mutableSnapshot.value = mutableSnapshot.value.copy(saving = true, lastErrorMessage = null)
        return client.postJson(
            CONFIG_PATH,
            JSONObject().put("master_volume", masterVolume).put("muted", muted),
        ).fold(
            onSuccess = { root ->
                parse(root.optJSONObject("data"))?.let {
                    mutableSnapshot.value = it.copy(connected = true)
                    AudioActionResult.Accepted
                } ?: reject("invalid_audio_config", "相框返回的音量设置不完整")
            },
            onFailure = { reject("device_unreachable", "无法保存音量，请确认相框已连接") },
        )
    }

    override suspend fun testSpeaker(): AudioActionResult {
        val current = mutableSnapshot.value
        if (current.muted) return reject("audio_muted", "请先关闭静音模式")
        mutableSnapshot.value = current.copy(testing = true, lastErrorMessage = null)
        return client.postJson(TEST_PATH, JSONObject()).fold(
            onSuccess = {
                mutableSnapshot.value = mutableSnapshot.value.copy(testing = false, lastErrorMessage = null)
                AudioActionResult.Accepted
            },
            onFailure = { error ->
                val message = if (error.message == "audio_busy") "小智正在使用音频，请稍后再试" else "扬声器测试未能开始"
                reject(error.message ?: "speaker_test_failed", message)
            },
        )
    }

    private fun parse(data: JSONObject?): AudioSnapshot? {
        data ?: return null
        val volume = data.optInt("master_volume", -1)
        if (volume !in 1..100 || !data.has("muted")) return null
        return AudioSnapshot(
            masterVolume = volume,
            muted = data.optBoolean("muted"),
            outputEnabled = data.optBoolean("output_enabled"),
            playing = data.optBoolean("playing"),
            source = data.optString("source", "idle"),
            connected = true,
        )
    }

    private fun reject(code: String, message: String): AudioActionResult.Rejected {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            connected = code != "device_unreachable" && mutableSnapshot.value.connected,
            saving = false,
            testing = false,
            lastErrorMessage = message,
        )
        return AudioActionResult.Rejected(code, message)
    }

    private companion object {
        const val STATUS_PATH = "/api/v1/audio/status"
        const val CONFIG_PATH = "/api/v1/audio/config"
        const val TEST_PATH = "/api/v1/audio/speaker-test"
    }
}
