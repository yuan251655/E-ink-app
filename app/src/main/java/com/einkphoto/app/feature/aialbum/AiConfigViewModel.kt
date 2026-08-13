package com.einkphoto.app.feature.aialbum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiConfigUiState(
    val configuration: AiProviderConfiguration = AiProviderConfiguration(),
    val profiles: List<AiModelProfile> = emptyList(),
    val profilesAvailable: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val testResult: AiConnectionTest? = null,
)

class AiConfigViewModel(private val repository: AiConfigRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AiConfigUiState())
    val state = mutableState.asStateFlow()

    fun refresh() = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(loading = true, message = null)
        repository.read().onSuccess { config ->
            repository.readProfiles().onSuccess { profiles ->
                val active = profiles.firstOrNull { it.active }
                mutableState.value = mutableState.value.copy(
                    configuration = config.copy(
                        profileId = active?.id ?: config.profileId,
                        profileName = active?.name ?: config.profileName,
                    ),
                    profiles = profiles,
                    profilesAvailable = true,
                    loading = false,
                )
            }.onFailure {
                // The deployed device may temporarily be on an earlier firmware.
                // Keep the existing single-profile configuration usable instead
                // of presenting an empty model screen as a successful list.
                mutableState.value = mutableState.value.copy(configuration = config, profilesAvailable = false, loading = false)
            }
        }.onFailure {
            mutableState.value = mutableState.value.copy(loading = false, message = "无法读取手机上的模型配置，请稍后重试")
        }
    }

    fun save(existingProfileId: String?, name: String, endpoint: String, imageModel: String, apiKey: String) = viewModelScope.launch {
        if (name.isBlank() || !endpoint.startsWith("https://") || imageModel.isBlank() || apiKey.length < 8) {
            mutableState.value = mutableState.value.copy(message = "请填写名称、HTTPS 服务地址、生图模型和有效的 API Key")
            return@launch
        }
        mutableState.value = mutableState.value.copy(saving = true, message = null, testResult = null)
        val profileId = existingProfileId.orEmpty().ifBlank { "model-${System.currentTimeMillis()}" }
        repository.saveProfile(profileId, name.trim(), endpoint, imageModel, apiKey).onSuccess { profile ->
            repository.activateProfile(profile.id).onSuccess {
                refreshAfterProfileChange("“${profile.name}”已保存并设为当前模型。Key 仅显示尾四位。")
            }.onFailure {
                mutableState.value = mutableState.value.copy(saving = false, message = "模型已保存，但暂时无法设为当前模型")
            }
        }.onFailure {
            mutableState.value = mutableState.value.copy(saving = false, message = "保存失败，请检查填写内容和手机存储状态")
        }
    }

    fun saveAndTest(existingProfileId: String?, name: String, endpoint: String, imageModel: String, apiKey: String) = viewModelScope.launch {
        if (name.isBlank() || !endpoint.startsWith("https://") || imageModel.isBlank() || (apiKey.isNotEmpty() && apiKey.length < 8)) {
            mutableState.value = mutableState.value.copy(message = "请填写名称、HTTPS 服务地址、生图模型和有效的 API Key")
            return@launch
        }
        mutableState.value = mutableState.value.copy(saving = true, message = "正在保存并检查配置…", testResult = null)
        val profileId = existingProfileId.orEmpty().ifBlank { "model-${System.currentTimeMillis()}" }
        val saved = repository.saveProfile(profileId, name.trim(), endpoint, imageModel, apiKey)
        saved.onFailure {
            mutableState.value = mutableState.value.copy(saving = false, message = "保存失败，请检查填写内容和手机存储状态")
            return@launch
        }
        val profile = saved.getOrThrow()
        repository.activateProfile(profile.id).onFailure {
            mutableState.value = mutableState.value.copy(saving = false, message = "模型已保存，但无法设为当前模型")
            return@launch
        }
        repository.testConnection(allowBillableTest = true).onSuccess { test ->
            val message = when {
                test.modelAvailable -> "模型连接成功：手机可访问 Seedream，API Key 有效，当前生图模型可用。测试未生成图片，不会写入 TF 卡。"
                test.authenticated -> "服务已连接且 API Key 有效，但当前模型不可用；请检查模型名称或开通权限。"
                else -> testMessage(test.code, test.providerMessage)
            }
            repository.read().onSuccess { config ->
                repository.readProfiles().onSuccess { profiles ->
                    val active = profiles.firstOrNull { it.active }
                    mutableState.value = mutableState.value.copy(configuration = config.copy(profileId = active?.id.orEmpty(), profileName = active?.name.orEmpty()), profiles = profiles, profilesAvailable = true, saving = false, message = message, testResult = test)
                }.onFailure {
                    mutableState.value = mutableState.value.copy(configuration = config, saving = false, message = message, testResult = test)
                }
            }.onFailure {
                mutableState.value = mutableState.value.copy(saving = false, message = message, testResult = test)
            }
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(saving = false, message = testMessage(error.message.orEmpty()))
        }
    }

    fun testSaved() = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(saving = true, message = "正在检查已保存的配置…", testResult = null)
        repository.testConnection(allowBillableTest = true).onSuccess { test ->
            val message = when {
                test.modelAvailable -> "模型连接成功：API Key 有效，当前图片模型可用。"
                test.authenticated -> "API Key 有效，但当前模型不可用；请检查模型 ID 或开通权限。"
                else -> testMessage(test.code, test.providerMessage)
            }
            mutableState.value = mutableState.value.copy(saving = false, message = message, testResult = test)
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(saving = false, message = testMessage(error.message.orEmpty()))
        }
    }

    private fun testMessage(code: String, providerMessage: String = ""): String = when (code) {
        "app_ai_configured" -> providerMessage.ifBlank { "已完成本地配置校验。" }
        "ai_http_400" -> "模型服务拒绝了本次验证：${providerMessage.ifBlank { "请检查模型 ID、API Key 权限和账户状态" }}"
        "ai_http_401" -> "模型服务已连接，但 API Key 无效或已失效。"
        "ai_http_403" -> "模型服务已连接，但当前 API Key 没有该模型的访问权限。"
        "ai_http_404", "ai_model_unavailable" -> "服务已连接，但未找到当前模型；请检查模型名称或接入点。"
        "ai_http_429" -> "模型服务暂时限流，请稍后再次测试。"
        "ai_network_failed" -> "手机无法访问互联网，请检查当前网络。"
        "ai_tls_failed" -> "手机无法建立安全连接，请检查系统时间和服务地址。"
        "ai_request_timeout" -> "连接模型服务超时，请检查网络后重试。"
        else -> "模型连接测试失败：${code.ifBlank { "未知错误" }}"
    }

    fun clear() = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(saving = true, message = null)
        repository.clear().onSuccess {
            mutableState.value = AiConfigUiState(message = "AI 模型配置已删除")
        }.onFailure {
            mutableState.value = mutableState.value.copy(saving = false, message = "删除失败，请稍后重试")
        }
    }

    fun activateProfile(id: String) = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(saving = true, message = "正在切换模型…", testResult = null)
        repository.activateProfile(id).onSuccess { refreshAfterProfileChange("当前模型已切换") }
            .onFailure { mutableState.value = mutableState.value.copy(saving = false, message = "切换失败，请稍后重试") }
    }

    fun deleteActiveProfile() = viewModelScope.launch {
        val profile = mutableState.value.profiles.firstOrNull { it.active }
            ?: return@launch run { mutableState.value = mutableState.value.copy(message = "没有可删除的已保存模型") }
        mutableState.value = mutableState.value.copy(saving = true, message = null)
        repository.deleteProfile(profile.id).onSuccess { refreshAfterProfileChange("“${profile.name}”已删除") }
            .onFailure { mutableState.value = mutableState.value.copy(saving = false, message = "删除失败，请稍后重试") }
    }

    private fun refreshAfterProfileChange(message: String) = viewModelScope.launch {
        repository.read().onSuccess { config ->
            repository.readProfiles().onSuccess { profiles ->
                val active = profiles.firstOrNull { it.active }
                mutableState.value = mutableState.value.copy(configuration = config.copy(profileId = active?.id.orEmpty(), profileName = active?.name.orEmpty()), profiles = profiles, profilesAvailable = true, saving = false, message = message)
            }.onFailure {
                mutableState.value = mutableState.value.copy(configuration = config, saving = false, message = message)
            }
        }.onFailure {
            mutableState.value = mutableState.value.copy(saving = false, message = "操作已提交，但暂时无法刷新模型列表")
        }
    }
}
