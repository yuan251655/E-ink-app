package com.einkphoto.app.ui.localalbum

import com.einkphoto.app.feature.localalbum.model.MediaId

internal sealed interface LocalAlbumRoute {
    data object Overview : LocalAlbumRoute
    data object Library : LocalAlbumRoute
    data object Import : LocalAlbumRoute
    data object Adapt : LocalAlbumRoute
    data object SixColorPreview : LocalAlbumRoute
    data object LocalConversion : LocalAlbumRoute
    data class Detail(val mediaId: MediaId) : LocalAlbumRoute
    data object Playback : LocalAlbumRoute
    data object Batch : LocalAlbumRoute
}
