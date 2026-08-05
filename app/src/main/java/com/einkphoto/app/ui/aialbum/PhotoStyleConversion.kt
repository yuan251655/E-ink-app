package com.einkphoto.app.ui.aialbum

import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.einkphoto.app.feature.aialbum.PhotoStyleCatalog
import com.einkphoto.app.feature.aialbum.PhotoStylePreset

@Composable
internal fun PhotoStyleGalleryScreen(onBack: () -> Unit, onSelect: (PhotoStylePreset) -> Unit, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            Text("照片风格转换", style = MaterialTheme.typography.titleLarge)
        }
        Text("选择一种风格后导入照片。提示词由系统固定处理。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(PhotoStyleCatalog.presets, key = { it.id }) { preset ->
                Card(Modifier.fillMaxWidth().clickable { onSelect(preset) }) {
                    Column {
                        Image(painterResource(preset.coverRes), contentDescription = "${preset.title}风格示例", modifier = Modifier.fillMaxWidth().aspectRatio(5f / 3f), contentScale = ContentScale.Crop)
                        Text(preset.title, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PhotoStyleDetailScreen(preset: PhotoStylePreset, onBack: () -> Unit, onStart: (PhotoStylePreset, Uri) -> Unit, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    var photo by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val preview = remember(photo) { photo?.let { uri -> context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photo = it }
    Column(modifier.fillMaxSize().padding(contentPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }; Text(preset.title, style = MaterialTheme.typography.titleLarge) }
        Image(painterResource(preset.coverRes), contentDescription = "${preset.title}风格示例", modifier = Modifier.fillMaxWidth().aspectRatio(5f / 3f), contentScale = ContentScale.Crop)
        Text("导入一张照片后，将按横向 5:3 构图生成。提示词由系统自动处理，无需输入。", style = MaterialTheme.typography.bodyMedium)
        if (photo == null) {
            Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.size(8.dp)); Text("导入手机照片") }
        } else {
            preview?.let { Image(it.asImageBitmap(), "照片横向 5:3 构图预览", Modifier.fillMaxWidth().aspectRatio(5f / 3f), contentScale = ContentScale.Crop) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button(onClick = { picker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("更换照片") }; Button(onClick = { onStart(preset, requireNotNull(photo)) }, modifier = Modifier.weight(1f)) { Text("开始转换") } }
        }
        Text("生成后先预览，确认保存后才会进入 AI 相册。", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
