package com.einkphoto.app.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val title: String,
    val subtitle: String,
    val deviceFeature: String?,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    LocalAlbum(
        title = "本地相册",
        subtitle = "管理手机导入与设备中的照片",
        deviceFeature = "本地相册",
        icon = Icons.Outlined.PhotoLibrary,
        selectedIcon = Icons.Rounded.PhotoLibrary,
    ),
    AiAlbum(
        title = "AI 相册",
        subtitle = "创作、同步并管理 AI 图片",
        deviceFeature = "AI 相册",
        icon = Icons.Outlined.AutoAwesome,
        selectedIcon = Icons.Rounded.AutoAwesome,
    ),
    Dashboard(
        title = "信息看板",
        subtitle = "配置天气、日期、备忘录与待办",
        deviceFeature = "信息看板",
        icon = Icons.Outlined.Dashboard,
        selectedIcon = Icons.Rounded.Dashboard,
    ),
    Settings(
        title = "设置",
        subtitle = "管理设备连接与应用偏好",
        deviceFeature = null,
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Rounded.Settings,
    ),
}

