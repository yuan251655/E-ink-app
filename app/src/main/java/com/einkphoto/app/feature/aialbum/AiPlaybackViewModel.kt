package com.einkphoto.app.feature.aialbum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.einkphoto.app.core.device.HttpLanDeviceTransport
import com.einkphoto.app.core.device.PlaybackTransportResult
import com.einkphoto.app.feature.localalbum.model.PlayMode
import com.einkphoto.app.feature.localalbum.model.PlayOrder
import com.einkphoto.app.feature.localalbum.model.PlaybackSettings
import com.einkphoto.app.feature.localalbum.model.PlaybackSyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AiPlaybackViewModel(private val client: HttpLanDeviceTransport = HttpLanDeviceTransport()) : ViewModel() {
    private val mutable = MutableStateFlow(PlaybackSettings(PlayMode.Paused, PlayOrder.Sequential, 1800))
    val state: StateFlow<PlaybackSettings> = mutable
    fun refresh() = viewModelScope.launch { apply(client.aiAlbumPlayback(), false) }
    fun save(mode: PlayMode, order: PlayOrder, interval: Int) = viewModelScope.launch {
        val old = mutable.value; mutable.value = old.copy(syncState = PlaybackSyncState.Saving)
        apply(client.saveAiAlbumPlayback("ai-playback-${System.currentTimeMillis()}", old.revision, if (mode == PlayMode.Auto) "auto" else "paused", interval, if (order == PlayOrder.Sequential) "sequential" else "random"), true)
    }
    private fun apply(result: PlaybackTransportResult, saving: Boolean) { when (result) {
        is PlaybackTransportResult.Success -> mutable.value = result.snapshot.let { PlaybackSettings(if (it.mode == "auto") PlayMode.Auto else PlayMode.Paused, if (it.order == "random") PlayOrder.Random else PlayOrder.Sequential, it.intervalSeconds, currentMediaId = it.currentMediaId?.let { id -> com.einkphoto.app.feature.localalbum.model.MediaId(id) }, nextPlayInSeconds = it.nextPlayInSeconds, nextPlayAtEpochMillis = it.nextPlayAtEpochMillis, revision = it.revision, stateRevision = it.stateRevision, syncState = PlaybackSyncState.Ready) }
        is PlaybackTransportResult.RevisionConflict -> { if (result.snapshot != null) apply(PlaybackTransportResult.Success(result.snapshot), false) else mutable.value = mutable.value.copy(syncState = PlaybackSyncState.Conflict) }
        is PlaybackTransportResult.Failure -> mutable.value = mutable.value.copy(syncState = PlaybackSyncState.Offline)
    } }
}
