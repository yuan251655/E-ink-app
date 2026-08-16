package com.einkphoto.app.feature.localalbum

import com.einkphoto.app.MainDispatcherRule
import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceRejection
import com.einkphoto.app.core.device.FakeDeviceSession
import com.einkphoto.app.feature.localalbum.data.FakeLocalAlbumRepository
import com.einkphoto.app.feature.localalbum.model.AfterDisplay
import com.einkphoto.app.feature.localalbum.model.PhoneSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LocalAlbumViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun rejectedDisplayIsReportedToCallerWithoutCreatingJob() = runTest(mainDispatcherRule.dispatcher) {
        val session = FakeDeviceSession()
        val repository = FakeLocalAlbumRepository(session)
        val viewModel = viewModel(session, repository)
        session.requestFeatureSwitch(DeviceFeature.InfoDashboard)
        session.finishFeatureSwitch(success = true)
        var callbackResult: DeviceCommandResult<*>? = null

        viewModel.display(repository.media.value[1].id) { callbackResult = it }
        testScheduler.advanceUntilIdle()

        assertEquals(DeviceRejection.FeatureNotActive, (callbackResult as DeviceCommandResult.Rejected).reason)
        assertEquals(null, repository.activeJob.value)
    }

    @Test
    fun nextRequestReportsAcceptedAndSecondRequestIsLockedWhileQueued() = runTest(mainDispatcherRule.dispatcher) {
        val session = FakeDeviceSession()
        val repository = FakeLocalAlbumRepository(session)
        val viewModel = viewModel(session, repository)
        var first: DeviceCommandResult<*>? = null
        var second: DeviceCommandResult<*>? = null

        viewModel.displayNext(onResult = { first = it })
        testScheduler.advanceUntilIdle()
        viewModel.displayNext(onResult = { second = it })

        assertTrue(first is DeviceCommandResult.Accepted)
        assertEquals(DeviceRejection.DisplayBusy, (second as DeviceCommandResult.Rejected).reason)
        repository.advanceDisplayJob()
        repository.finishDisplayJob(success = true)
        assertEquals(repository.media.value[1].id, repository.currentDisplay.value.mediaId)
    }

    @Test
    fun deleteCallbackDistinguishesProtectedAndDeletedMedia() = runTest(mainDispatcherRule.dispatcher) {
        val session = FakeDeviceSession()
        val repository = FakeLocalAlbumRepository(session)
        val viewModel = viewModel(session, repository)
        var protectedResult: DeviceCommandResult<*>? = null
        var deletedResult: DeviceCommandResult<*>? = null
        val protected = repository.media.value.first().id
        val deletable = repository.media.value.last().id

        viewModel.delete(protected) { protectedResult = it }
        testScheduler.advanceUntilIdle()
        viewModel.delete(deletable) { deletedResult = it }
        testScheduler.advanceUntilIdle()

        assertEquals(DeviceRejection.MediaProtected, (protectedResult as DeviceCommandResult.Rejected).reason)
        assertTrue(deletedResult is DeviceCommandResult.Accepted)
        assertTrue(repository.media.value.none { it.id == deletable })
    }

    @Test
    fun portraitPhotosDefaultToClockwiseFrameRotation() {
        val portrait = PhoneSource("portrait", "file:///portrait.jpg", "portrait.jpg", 1200, 1600)
        val landscape = PhoneSource("landscape", "file:///landscape.jpg", "landscape.jpg", 1600, 1200)

        assertEquals(1, defaultAdaptationFor(portrait).quarterTurnsClockwise)
        assertEquals(0, defaultAdaptationFor(landscape).quarterTurnsClockwise)
    }

    private fun viewModel(
        session: FakeDeviceSession,
        repository: FakeLocalAlbumRepository,
    ) = LocalAlbumViewModel(session, repository, repository, repository)
}
