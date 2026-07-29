package com.einkphoto.app.feature.localalbum.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.einkphoto.app.core.device.DevelopmentApHttpClient
import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceJob
import com.einkphoto.app.core.device.DeviceJobId
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.core.device.DeviceRejection
import com.einkphoto.app.feature.localalbum.model.AfterDisplay
import com.einkphoto.app.feature.localalbum.model.ConversionDraft
import com.einkphoto.app.feature.localalbum.model.CurrentDisplay
import com.einkphoto.app.feature.localalbum.model.DisplayResult
import com.einkphoto.app.feature.localalbum.model.MediaAvailability
import com.einkphoto.app.feature.localalbum.model.MediaCategory
import com.einkphoto.app.feature.localalbum.model.MediaId
import com.einkphoto.app.feature.localalbum.model.MediaItem
import com.einkphoto.app.feature.localalbum.model.PlayMode
import com.einkphoto.app.feature.localalbum.model.PlayOrder
import com.einkphoto.app.feature.localalbum.model.PlaybackSettings
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Real AP upload adapter. Saving completes only after the device has atomically admitted the frame. */
class HttpLocalAlbumUploadRepository(
    private val appContext: Context,
    private val client: DevelopmentApHttpClient = DevelopmentApHttpClient(),
) : MediaRepository, PlaybackRepository, DisplayRepository, UploadRepository {
    private val mutableMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    override val media: StateFlow<List<MediaItem>> = mutableMedia.asStateFlow()
    private val mutableCurrentDisplay = MutableStateFlow(
        CurrentDisplay(null, DeviceFeature.LocalAlbum, DisplayResult.Idle, null),
    )
    override val currentDisplay: StateFlow<CurrentDisplay> = mutableCurrentDisplay.asStateFlow()
    private val mutableSettings = MutableStateFlow(PlaybackSettings(PlayMode.Auto, PlayOrder.Sequential, 30))
    override val settings: StateFlow<PlaybackSettings> = mutableSettings.asStateFlow()
    private val mutableActiveJob = MutableStateFlow<DeviceJob?>(null)
    override val activeJob: StateFlow<DeviceJob?> = mutableActiveJob.asStateFlow()
    private val previewDirectory = File(appContext.cacheDir, "device-media-preview").apply { mkdirs() }

    override suspend fun refresh(): DeviceCommandResult<Unit> = client.get("/api/v1/media?limit=8&cursor=0").fold(
        onSuccess = { root ->
            val items = root.getJSONObject("data").optJSONArray("items")
            mutableMedia.value = buildList {
                if (items != null) {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        if (item.optString("category") != "local") continue
                        val id = item.optString("media_id")
                        if (id.isBlank()) continue
                        add(
                            MediaItem(
                                id = MediaId(id),
                                category = MediaCategory.Local,
                                displayName = item.optString("display_name", id).ifBlank { id },
                                previewUri = devicePreviewUri(id),
                                sourceWidthPx = item.optInt("width_px", 800).coerceAtLeast(1),
                                sourceHeightPx = item.optInt("height_px", 480).coerceAtLeast(1),
                                sizeBytes = 192_000,
                                availability = MediaAvailability.Ready,
                                createdAtEpochMillis = item.optLong("created_at_epoch_s", 0L) * 1_000L,
                            ),
                        )
                    }
                }
            }
            DeviceCommandResult.Accepted(Unit)
        },
        onFailure = { DeviceCommandResult.Rejected(DeviceRejection.Offline) },
    )

    override suspend fun delete(mediaId: MediaId): DeviceCommandResult<Unit> = DeviceCommandResult.Rejected(DeviceRejection.Unsupported)

    override suspend fun save(settings: PlaybackSettings): DeviceCommandResult<Unit> {
        mutableSettings.value = settings
        return DeviceCommandResult.Accepted(Unit)
    }

    override suspend fun requestDisplay(mediaId: MediaId, afterDisplay: AfterDisplay): DeviceCommandResult<DeviceJobId> {
        val requestId = "display-${System.currentTimeMillis()}-${mediaId.value.take(20)}"
        return client.postJson(
            "/api/v1/media/${mediaId.value}/display",
            JSONObject().put("request_id", requestId).put("after_display", if (afterDisplay == AfterDisplay.Hold) "hold" else "continue"),
        ).fold(
            onSuccess = { root ->
                val id = root.getJSONObject("data").optLong("job_id", -1L)
                if (id <= 0L) return@fold DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
                val job = DeviceJob(DeviceJobId("display-$id"), "display_local_media", DeviceJobState.Queued, "已提交显示请求")
                mutableActiveJob.value = job
                mutableCurrentDisplay.value = mutableCurrentDisplay.value.copy(result = DisplayResult.Refreshing)
                DeviceCommandResult.Accepted(job.id)
            },
            onFailure = { error ->
                DeviceCommandResult.Rejected(
                    if (error.message?.contains("display_busy", ignoreCase = true) == true) {
                        DeviceRejection.DisplayBusy
                    } else {
                        DeviceRejection.Offline
                    },
                )
            },
        )
    }

    override suspend fun submit(
        draft: ConversionDraft,
        mode: UploadMode,
        requestId: String,
    ): DeviceCommandResult<DeviceJobId> {
        if (mode != UploadMode.SourceAndBin || draft.generatedFrameBytes != draft.profile.frameBytes) {
            return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        }
        val framePath = Uri.parse(draft.candidateBinUri).path ?: return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        val mediaId = "local-${draft.draftId}".replace(Regex("[^A-Za-z0-9_-]"), "-")
        return client.uploadLocalFrame(requestId, mediaId, draft.source.displayName, File(framePath)).fold(
            onSuccess = { root ->
                val jobId = root.optJSONObject("data")?.optLong("job_id", -1L) ?: -1L
                if (jobId <= 0L) return@fold DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
                refresh()
                DeviceCommandResult.Accepted(DeviceJobId("upload-$jobId"))
            },
            onFailure = { error ->
                val reason = when {
                    error.message?.contains("storage", ignoreCase = true) == true -> DeviceRejection.StorageUnavailable
                    error.message?.contains("validation", ignoreCase = true) == true -> DeviceRejection.Unsupported
                    else -> DeviceRejection.Offline
                }
                DeviceCommandResult.Rejected(reason)
            },
        )
    }

    /**
     * Saved media must be previewed from device-owned bytes, never from a
     * temporary source URI retained only by the previous App process.
     */
    private suspend fun devicePreviewUri(mediaId: String): String? {
        val target = File(previewDirectory, "$mediaId.png")
        if (target.isFile && target.length() > 0L) return Uri.fromFile(target).toString()
        val frame = client.downloadMediaPreview(mediaId).getOrNull() ?: return null
        return runCatching {
            val bitmap = Bitmap.createBitmap(800, 480, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(800 * 480)
            frame.forEachIndexed { byteIndex, packed ->
                val high = (packed.toInt() ushr 4) and 0x0f
                val low = packed.toInt() and 0x0f
                val pixelIndex = byteIndex * 2
                pixels[pixelIndex] = einkColor(high)
                pixels[pixelIndex + 1] = einkColor(low)
            }
            val temporary = File(previewDirectory, "$mediaId.tmp")
            bitmap.setPixels(pixels, 0, 800, 0, 0, 800, 480)
            FileOutputStream(temporary).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            check(temporary.renameTo(target))
            bitmap.recycle()
            Uri.fromFile(target).toString()
        }.getOrNull()
    }

    private fun einkColor(index: Int): Int = when (index) {
        0 -> 0xff000000.toInt() // black
        1 -> 0xffffffff.toInt() // white
        2 -> 0xffffe600.toInt() // yellow
        3 -> 0xffd92d2d.toInt() // red
        5 -> 0xff245bc6.toInt() // blue
        6 -> 0xff1c9b54.toInt() // green
        else -> 0xffffffff.toInt()
    }
}
