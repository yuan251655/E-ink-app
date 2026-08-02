package com.einkphoto.app.feature.aialbum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class XiaozhiSettingsUiState(
    val status: XiaozhiSettingsStatus = XiaozhiSettingsStatus(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
)

class XiaozhiSettingsViewModel(private val repository: XiaozhiSettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(XiaozhiSettingsUiState())
    val state = mutableState.asStateFlow()
    private var pollJob: Job? = null
    /**
     * Loading is only a first-load affordance. The settings page polls in the
     * background, so changing this flag for every heartbeat visibly flashes
     * the status text and disables/enables the switch on real phones.
     */
    private var hasLoadedStatus = false
    private var initialLoadCompleted = false

    fun setActive(active: Boolean) {
        if (!active) {
            pollJob?.cancel()
            pollJob = null
            return
        }
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                delay(2_000)
            }
        }
    }

    private suspend fun refreshOnce() {
        if (!initialLoadCompleted && !mutableState.value.loading) {
            mutableState.value = mutableState.value.copy(loading = true)
        }
        repository.status().onSuccess { status ->
            initialLoadCompleted = true
            hasLoadedStatus = true
            // StateFlow suppresses equal values, so an unchanged device status
            // does not recompose the whole settings screen every two seconds.
            mutableState.value = mutableState.value.copy(
                status = status,
                loading = false,
                errorMessage = null,
            )
        }.onFailure {
            initialLoadCompleted = true
            val previous = mutableState.value
            if (!hasLoadedStatus) {
                mutableState.value = previous.copy(
                    loading = false,
                    errorMessage = "无法读取小智状态，请确认相框已连接",
                )
            } else if (previous.errorMessage == null) {
                // Keep the last confirmed status and show one stable failure
                // message. Do not clear/recreate it on each retry.
                mutableState.value = previous.copy(
                    errorMessage = "无法读取小智状态，请确认相框已连接",
                )
            }
        }
    }

    override fun onCleared() { pollJob?.cancel() }
}
