package com.einkphoto.app.feature.localalbum.data

import android.content.Context
import com.einkphoto.app.core.device.FakeDeviceSession
import com.einkphoto.app.core.device.DeviceSession
import com.einkphoto.app.core.device.HttpLanDeviceTransport

data class LocalAlbumDemoDependencies(
    val session: DeviceSession,
    val mediaRepository: MediaRepository,
    val playbackRepository: PlaybackRepository,
    val displayRepository: DisplayRepository,
    val uploadRepository: UploadRepository?,
    val demoController: DemoLocalAlbumController?,
) {
    companion object {
        fun create(context: Context, session: DeviceSession = FakeDeviceSession()): LocalAlbumDemoDependencies {
            val repository = FakeLocalAlbumRepository(session)
            if (session.snapshot.value.isDemo) {
                return LocalAlbumDemoDependencies(session, repository, repository, repository, repository, repository)
            }
            val transport = HttpLanDeviceTransport()
            val realRepository = LanLocalAlbumReadRepository(context.applicationContext, transport)
            val uploadRepository = LanLocalAlbumUploadRepository(context.applicationContext, transport)
            // Upload admission is separate from display: it can write an atomic TF MediaItem but
            // cannot request screen refresh, previous/next, or any legacy adapter operation.
            return LocalAlbumDemoDependencies(session, realRepository, realRepository, realRepository, uploadRepository, null)
        }
    }
}
