package com.einkphoto.app.ui.localalbum

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.einkphoto.app.feature.localalbum.model.PhoneSource

internal data class PhoneAlbumPhoto(
    val uri: Uri,
    val name: String,
    val albumId: String,
    val albumName: String,
    val width: Int,
    val height: Int,
)

@Composable
internal fun PhoneAlbumPickerScreen(
    context: Context,
    onBack: () -> Unit,
    onConfirm: (List<Uri>) -> Unit,
) {
    var photos by remember { mutableStateOf<List<PhoneAlbumPhoto>>(emptyList()) }
    var album by remember { mutableStateOf("all") }
    var selected by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    LaunchedEffect(Unit) { photos = queryPhonePhotos(context) }
    val albums = remember(photos) {
        listOf("all" to "全部") + photos.distinctBy { it.albumId }.map { it.albumId to it.albumName }
    }
    val visible = if (album == "all") photos else photos.filter { it.albumId == album }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                Text("选择手机相册", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { onConfirm(selected.toList()) }, enabled = selected.isNotEmpty()) {
                    Text("完成" + if (selected.isEmpty()) "" else " (" + selected.size + ")")
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                albums.forEach { (id, name) ->
                    FilterChip(selected = album == id, onClick = { album = id }, label = { Text(name) })
                }
            }
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有读取到照片，请检查照片权限", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(visible, key = { it.uri.toString() }) { photo ->
                        val isSelected = photo.uri in selected
                        Box {
                            PhoneSourcePreview(
                                PhoneSource("album-" + photo.uri.hashCode(), photo.uri.toString(), photo.name, photo.width, photo.height),
                                photo.name,
                                Modifier.fillMaxWidth().size(112.dp),
                            )
                            IconButton(
                                onClick = { selected = if (isSelected) selected - photo.uri else selected + photo.uri },
                                modifier = Modifier.align(Alignment.BottomEnd),
                            ) {
                                Icon(
                                    if (isSelected) Icons.Outlined.Check else Icons.Outlined.PhotoLibrary,
                                    contentDescription = if (isSelected) "已选择" else "选择",
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun queryPhonePhotos(context: Context): List<PhoneAlbumPhoto> {
    val projection = arrayOf(
        MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
    )
    val result = ArrayList<PhoneAlbumPhoto>()
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
        MediaStore.Images.Media.DATE_ADDED + " DESC",
    )?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val bucketId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
        val bucketName = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val width = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val height = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        while (cursor.moveToNext()) {
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(id))
            result += PhoneAlbumPhoto(uri, cursor.getString(name) ?: "未命名图片", cursor.getString(bucketId) ?: "all", cursor.getString(bucketName) ?: "其他", cursor.getInt(width).coerceAtLeast(1), cursor.getInt(height).coerceAtLeast(1))
        }
    }
    return result
}
