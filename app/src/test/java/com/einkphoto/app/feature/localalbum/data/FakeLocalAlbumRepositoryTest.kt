package com.einkphoto.app.feature.localalbum.data

import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceRejection
import com.einkphoto.app.core.device.FakeDeviceSession
import com.einkphoto.app.core.device.FakeDeviceScenario
import com.einkphoto.app.feature.localalbum.model.AfterDisplay
import com.einkphoto.app.feature.localalbum.model.ConversionDraft
import com.einkphoto.app.feature.localalbum.model.ConversionStage
import com.einkphoto.app.feature.localalbum.model.FitMode
import com.einkphoto.app.feature.localalbum.model.MediaProtectionReason
import com.einkphoto.app.feature.localalbum.model.PhoneSource
import com.einkphoto.app.feature.localalbum.model.PlayMode
import com.einkphoto.app.core.device.DisplayProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeLocalAlbumRepositoryTest {
    @Test
    fun fakeCurrentDisplayIsDeviceOwnedAndStartsOnCommittedMedia() {
        val session = FakeDeviceSession()
        val repository = FakeLocalAlbumRepository(session)

        assertEquals(repository.media.value.first().id, repository.currentDisplay.value.mediaId)
        assertEquals(DeviceFeature.LocalAlbum, repository.currentDisplay.value.feature)
    }

    @Test
    fun displayRequestRequiresLocalAlbumToBeActive() = runTest {
        val session = FakeDeviceSession()
        val repository = FakeLocalAlbumRepository(session)
        session.requestFeatureSwitch(DeviceFeature.InfoDashboard)
        session.finishFeatureSwitch(success = true)

        val result = repository.requestDisplay(repository.media.value.first().id, AfterDisplay.Continue)

        assertTrue(result is DeviceCommandResult.Rejected)
        assertEquals(DeviceRejection.FeatureNotActive, (result as DeviceCommandResult.Rejected).reason)
    }

    @Test
    fun queuedDisplayIsCompletedFromSavedTargetAndHoldStrategy() = runTest {
        val session = FakeDeviceSession()
        val repository = FakeLocalAlbumRepository(session)
        val oldTarget = repository.media.value.first().id
        val newTarget = repository.media.value[1].id

        repository.requestDisplay(newTarget, AfterDisplay.Hold)

        assertEquals(oldTarget, repository.currentDisplay.value.mediaId)
        assertEquals(com.einkphoto.app.core.device.DeviceJobState.Running, repository.advanceDisplayJob()?.state)
        repository.finishDisplayJob(success = true)
        assertEquals(newTarget, repository.currentDisplay.value.mediaId)
        assertEquals(PlayMode.Paused, repository.settings.value.mode)
        assertTrue(MediaProtectionReason.CurrentDisplay !in repository.media.value.first().protectionReasons)
        assertTrue(MediaProtectionReason.CurrentDisplay in repository.media.value[1].protectionReasons)
    }

    @Test
    fun continueStrategyRestoresAutomaticPlaybackOnlyAfterSuccess() = runTest {
        val session = FakeDeviceSession()
        val repository = FakeLocalAlbumRepository(session)
        repository.save(repository.settings.value.copy(mode = PlayMode.Paused))
        val target = repository.media.value[2].id

        repository.requestDisplay(target, AfterDisplay.Continue)
        assertEquals(PlayMode.Paused, repository.settings.value.mode)
        repository.advanceDisplayJob()
        repository.finishDisplayJob(success = true)

        assertEquals(target, repository.currentDisplay.value.mediaId)
        assertEquals(PlayMode.Auto, repository.settings.value.mode)
    }

    @Test
    fun sleepingDevicePreservesSleepingRejection() = runTest {
        val session = FakeDeviceSession(FakeDeviceScenario.Sleeping)
        val repository = FakeLocalAlbumRepository(session)

        val result = repository.requestDisplay(repository.media.value.first().id, AfterDisplay.Continue)

        assertEquals(DeviceRejection.Sleeping, (result as DeviceCommandResult.Rejected).reason)
    }

    @Test
    fun protectedMediaIsRejectedAndUnprotectedMediaCanBeDeleted() = runTest {
        val session = FakeDeviceSession()
        val repository = FakeLocalAlbumRepository(session)
        val protected = repository.media.value.first().id
        val deletable = repository.media.value[1].id

        val protectedResult = repository.delete(protected)
        val deleteResult = repository.delete(deletable)

        assertEquals(DeviceRejection.MediaProtected, (protectedResult as DeviceCommandResult.Rejected).reason)
        assertTrue(deleteResult is DeviceCommandResult.Accepted)
        assertTrue(repository.media.value.none { it.id == deletable })
    }

    @Test
    fun queuedMockUploadsAreSerialAndEachJobCanBeRetriedIndependently() = runTest {
        val session = FakeDeviceSession()
        val repository = FakeLocalAlbumRepository(session)
        val first = readyDraft("one")
        val second = readyDraft("two")

        val firstJob = repository.submit(first, UploadMode.SourceAndBin, "request-one").acceptedJobId()
        val secondJob = repository.submit(second, UploadMode.SourceAndBin, "request-two").acceptedJobId()

        // Idempotent submission while the second item is queued must not create another job.
        assertEquals(secondJob, repository.submit(second, UploadMode.SourceAndBin, "request-two").acceptedJobId())
        assertEquals(null, repository.advanceUploadJob(secondJob))

        advanceToCommit(repository, firstJob)
        assertEquals(com.einkphoto.app.core.device.DeviceJobState.Success, repository.finishUploadJob(firstJob, success = true)?.state)
        assertTrue(repository.media.value.any { it.displayName == "one.jpg" })
        assertTrue(repository.media.value.none { it.displayName == "two.jpg" })

        advanceToCommit(repository, secondJob)
        assertEquals(com.einkphoto.app.core.device.DeviceJobState.Failed, repository.finishUploadJob(secondJob, success = false)?.state)
        assertTrue(repository.media.value.none { it.displayName == "two.jpg" })

        // A failed request may be resubmitted with the same request id; the admitted first item stays intact.
        val retryJob = repository.submit(second, UploadMode.SourceAndBin, "request-two").acceptedJobId()
        advanceToCommit(repository, retryJob)
        assertEquals(com.einkphoto.app.core.device.DeviceJobState.Success, repository.finishUploadJob(retryJob, success = true)?.state)
        assertEquals(1, repository.media.value.count { it.displayName == "one.jpg" })
        assertEquals(1, repository.media.value.count { it.displayName == "two.jpg" })
    }

    private fun advanceToCommit(repository: FakeLocalAlbumRepository, jobId: com.einkphoto.app.core.device.DeviceJobId) {
        repeat(3) { assertTrue(repository.advanceUploadJob(jobId) != null) }
    }

    private fun DeviceCommandResult<com.einkphoto.app.core.device.DeviceJobId>.acceptedJobId() =
        (this as DeviceCommandResult.Accepted).value

    private fun readyDraft(id: String): ConversionDraft {
        val source = PhoneSource("phone-$id", "file:///$id.jpg", "$id.jpg", 1200, 1600)
        val profile = DisplayProfile(800, 480, 192_000, listOf("black", "white", "red", "green", "blue", "yellow"), "official")
        return ConversionDraft(
            draftId = "draft-$id",
            source = source,
            profile = profile,
            fitMode = FitMode.CropToFill,
            quarterTurnsClockwise = 0,
            stage = ConversionStage.Ready,
            previewUri = "file:///$id.png",
            candidateBinUri = "file:///$id.bin",
            generatedFrameBytes = profile.frameBytes,
            algorithmVersion = "official-floyd-steinberg-six-color-v1",
            localValidationPassed = true,
        )
    }
}
