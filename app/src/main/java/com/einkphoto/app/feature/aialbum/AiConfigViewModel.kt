package com.einkphoto.app.feature.aialbum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiConfigUiState(
    val configuration: AiProviderConfiguration = AiProviderConfiguration(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
)

class AiConfigViewModel(private val repository: AiConfigRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AiConfigUiState())
    val state = mutableState.asStateFlow()

    fun refresh() = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(loading = true, message = null)
        repository.read().onSuccess { config ->
            mutableState.value = mutableState.value.copy(configuration = config, loading = false)
        }.onFailure {
            mutableState.value = mutableState.value.copy(loading = false, message = "无法读取模型配置，请确认相框已连接")
        }
    }

    fun save(endpoint: String, imageModel: String, apiKey: String) = viewModelScope.launch {
        if (!endpoint.startsWith("https://") || imageModel.isBlank() || apiKey.length < 8) {
            mutableState.value = mutableState.value.copy(message = "请填写 HTTPS 服务地址、生图模型和有效的 API Key")
            return@launch
        }
        mutableState.value = mutableState.value.copy(saving = true, message = null)
        repository.save(endpoint, imageModel, apiKey).onSuccess { config ->
            mutableState.value = mutableState.value.copy(
                configuration = config,
                saving = false,
                message = "配置已安全保存，Key 仅显示尾四位。首次生成图片时将验证网络和鉴权，不会额外产生测试费用。",
            )
        }.onFailure {
            mutableState.value = mutableState.value.copy(saving = false, message = "保存失败，请检查相框连接和填写内容")
        }
    }

    fun saveAndTest(endpoint: String, imageModel: String, apiKey: String) = viewModelScope.launch {
        if (!endpoint.startsWith("https://") || imageModel.isBlank() || (apiKey.isNotEmpty() && apiKey.length < 8)) {
            mutableState.value = mutableState.value.copy(message = "请填写 HTTPS 服务地址、生图模型和有效的 API Key")
            return@launch
        }
        mutableState.value = mutableState.value.copy(saving = true, message = "正在保存并检查模型连接…")
        repository.save(endpoint, imageModel, apiKey).onFailure {
            mutableState.value = mutableState.value.copy(saving = false, message = "保存失败，请检查相框连接和填写内容")
            return@launch
        }
        repository.testConnection().onSuccess { test ->
            val message = when {
                test.modelAvailable -> "模型连接成功：相框已联网，API Key 有效，当前生图模型可用。测试未生成图片，不会写入 TF 卡。"
                test.authenticated -> "服务已连接且 API Key 有效，但当前模型不可用；请检查模型名称或开通权限。"
                else -> testMessage(test.code)
            }
            repository.read().onSuccess { config ->
                mutableState.value = mutableState.value.copy(configuration = config, saving = false, message = message)
            }.onFailure {
                mutableState.value = mutableState.value.copy(saving = false, message = message)
            }
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(saving = false, message = testMessage(error.message.orEmpty()))
        }
    }

    private fun testMessage(code: String): String = when (code) {
        "ai_http_401" -> "模型服务已连接，但 API Key 无效或已失效。"
        "ai_http_403" -> "模型服务已连接，但当前 API Key 没有该模型的访问权限。"
        "ai_http_404", "ai_model_unavailable" -> "服务已连接，但未找到当前模型；请检查模型名称或接入点。"
        "ai_http_429" -> "模型服务暂时限流，请稍后再次测试。"
        "ai_network_failed" -> "相框无法访问互联网，请先检查 STA 网络。"
        "ai_tls_failed" -> "相框无法建立安全连接，请检查网络时间和服务地址。"
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
}
