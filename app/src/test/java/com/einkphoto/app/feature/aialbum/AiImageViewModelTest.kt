package com.einkphoto.app.feature.aialbum

import com.einkphoto.app.MainDispatcherRule
import com.einkphoto.app.core.device.DeviceCapabilities
import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceConnectionState
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceJob
import com.einkphoto.app.core.device.DeviceJobId
import com.einkphoto.app.core.device.DeviceJobSnapshot
import com.einkphoto.app.core.device.DeviceRejection
import com.einkphoto.app.core.device.DeviceSession
import com.einkphoto.app.core.device.DeviceSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AiImageViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun refreshPublishesOnlyRepositoryDataAndReadyState() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAiImageRepository().apply {
            mutableImages.value = listOf(item("ai-1"))
        }
        val viewModel = AiImageViewModel(onlineSession(), repository)

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(AiImageLoadState.Ready, viewModel.state.value.loadState)
        assertEquals(listOf("ai-1"), viewModel.state.value.images.map { it.id })
        assertEquals(1, repository.refreshCalls)
    }

    @Test fun offlineRefreshDoesNotCallRepositoryOrInventAnEmptySuccess() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAiImageRepository()
        val viewModel = AiImageViewModel(offlineSession(), repository)

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(AiImageLoadState.Offline, viewModel.state.value.loadState)
        assertEquals(0, repository.refreshCalls)
    }

    @Test fun mediaEndpointFailureDoesNotCallAnOnlineFrameDisconnected() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAiImageRepository().apply {
            refreshResult = DeviceCommandResult.Rejected(DeviceRejection.Offline)
        }
        val viewModel = AiImageViewModel(onlineSession(), repository)

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(AiImageLoadState.Error, viewModel.state.value.loadState)
        assertTrue(viewModel.state.value.errorMessage.orEmpty().contains("暂时无法读取 AI 图片"))
        assertFalse(viewModel.state.value.errorMessage.orEmpty().contains("未连接"))
    }

    @Test fun readyCacheBecomesOfflineAsSoonAsTheDeviceDisconnects() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAiImageRepository().apply {
            mutableImages.value = listOf(item("ai-cached"))
        }
        val session = TestSession(DeviceConnectionState.Online)
        val viewModel = AiImageViewModel(session, repository)

        viewModel.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(AiImageLoadState.Ready, viewModel.state.value.loadState)

        session.setConnection(DeviceConnectionState.Offline)
        testScheduler.advanceUntilIdle()

        assertEquals(AiImageLoadState.Offline, viewModel.state.value.loadState)
        assertEquals(listOf("ai-cached"), viewModel.state.value.images.map { it.id })
    }

    @Test fun paginationAndActionFailuresRemainExplicit() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAiImageRepository().apply {
            mutableHasMore.value = true
            displayResult = DeviceCommandResult.Rejected(DeviceRejection.FeatureNotActive)
        }
        val viewModel = AiImageViewModel(onlineSession(), repository)

        viewModel.loadMore()
        testScheduler.advanceUntilIdle()
        assertEquals(1, repository.loadMoreCalls)
        assertFalse(viewModel.state.value.loadingMore)

        viewModel.display("ai-1")
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.actionMessage.orEmpty().contains("AI 相册模式"))
        assertFalse(viewModel.state.value.actionMessage.orEmpty().contains("成功"))
    }

    @Test fun previewFallbackExportIsClearlyNamed() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAiImageRepository().apply {
            exportResult = DeviceCommandResult.Accepted(AiImageExportResult(AiImageExportKind.SixColorPreview, "画面-六色预览.png"))
        }
        val viewModel = AiImageViewModel(onlineSession(), repository)

        viewModel.saveToPhone("ai-1")
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.actionMessage.orEmpty().contains("六色预览图"))
    }

    private fun item(id: String) = AiImageItem(id, id, 0L, 192_000, 1L, null, false)

    private fun onlineSession() = TestSession(DeviceConnectionState.Online)
    private fun offlineSession() = TestSession(DeviceConnectionState.Offline)

    private class TestSession(connection: DeviceConnectionState) : DeviceSession {
        private val mutable = MutableStateFlow(DeviceSnapshot("test", "test", false, connection, DeviceFeature.AiAlbum, false, null, null))
        override val snapshot: StateFlow<DeviceSnapshot> = mutable
        fun setConnection(connection: DeviceConnectionState) {
            mutable.value = mutable.value.copy(connection = connection)
        }
        override suspend fun refreshSnapshot() = DeviceCommandResult.Accepted(mutable.value)
        override suspend fun requestFeatureSwitch(feature: DeviceFeature) = DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        override suspend fun modeSwitchJob(jobId: DeviceJobId) = DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
    }

    private class FakeAiImageRepository : AiImageRepository {
        val mutableImages = MutableStateFlow<List<AiImageItem>>(emptyList())
        override val images: StateFlow<List<AiImageItem>> = mutableImages
        override val activeJob = MutableStateFlow<DeviceJob?>(null)
        val mutableHasMore = MutableStateFlow(false)
        override val hasMore: StateFlow<Boolean> = mutableHasMore
        var refreshCalls = 0
        var loadMoreCalls = 0
        var displayResult: DeviceCommandResult<DeviceJobId> = DeviceCommandResult.Accepted(DeviceJobId("display-1"))
        var exportResult: DeviceCommandResult<AiImageExportResult> = DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        var refreshResult: DeviceCommandResult<Unit> = DeviceCommandResult.Accepted(Unit)
        override suspend fun refresh(): DeviceCommandResult<Unit> { refreshCalls += 1; return refreshResult }
        override suspend fun loadMore(): DeviceCommandResult<Unit> { loadMoreCalls += 1; return DeviceCommandResult.Accepted(Unit) }
        override suspend fun display(mediaId: String) = displayResult
        override suspend fun delete(mediaId: String): DeviceCommandResult<Unit> = DeviceCommandResult.Accepted(Unit)
        override suspend fun exportToPhone(mediaId: String) = exportResult
        override fun close() = Unit
    }
}
