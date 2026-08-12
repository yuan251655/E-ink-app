package com.einkphoto.app.ui.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WbCloudy
import com.einkphoto.app.ui.components.AppleAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.einkphoto.app.R
import com.einkphoto.app.core.device.DeviceContentKind
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceSnapshot
import com.einkphoto.app.feature.mode.ModeSwitchUiState
import com.einkphoto.app.ui.components.ModeFeatureHeader
import com.einkphoto.app.ui.components.ModeSwitchStatusCard
import com.einkphoto.app.ui.components.crossFeatureDisplayText
import com.einkphoto.app.ui.components.modeCoverDrawableRes
import com.einkphoto.app.ui.components.hierarchicalPageTransition
import com.einkphoto.app.ui.theme.EInkPhotoTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class DashboardRoute { Overview, Layout, Today, Weather, Time }

private enum class DashboardLayoutOption(
    val title: String,
    val description: String,
    val compactTitle: String,
) {
    WeatherDate("天气日期", "出门前速览：天气、温度、日期", "天气与日期"),
    DateMemoTodo("日期待办", "今日计划：重点与三项待办", "今日计划"),
    WeatherMemoTodo("轻量综合", "天气、重点与两项待办", "轻量综合"),
}

private data class DashboardTodo(val id: String, val title: String, val completed: Boolean = false) {
    constructor(id: Long, title: String, completed: Boolean = false) : this(id.toString(), title, completed)
}

private fun dashboardLayoutApiValue(option: DashboardLayoutOption): String = when (option) {
    DashboardLayoutOption.WeatherDate -> "weather_date"
    DashboardLayoutOption.DateMemoTodo -> "date_memo_todo"
    DashboardLayoutOption.WeatherMemoTodo -> "weather_memo_todo"
}

private fun dashboardLayoutOption(apiValue: String): DashboardLayoutOption = when (apiValue) {
    "weather_date" -> DashboardLayoutOption.WeatherDate
    "date_memo_todo" -> DashboardLayoutOption.DateMemoTodo
    else -> DashboardLayoutOption.WeatherMemoTodo
}

/**
 * App-rendered dashboard flow: the App creates the six-colour frame, then the
 * device atomically stores and displays the resulting dashboard media item.
 */
@Composable
fun InfoDashboardHost(
    contentPadding: PaddingValues,
    device: DeviceSnapshot,
    modeSwitchState: ModeSwitchUiState,
    onSwitchMode: (DeviceFeature) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dashboardFrames = remember(context) { DashboardFrameRepository(context.applicationContext) }
    val dashboardData = remember { DashboardDataRepository() }
    var route by rememberSaveable { mutableStateOf(DashboardRoute.Overview) }
    var selectedLayoutName by rememberSaveable { mutableStateOf(DashboardLayoutOption.WeatherMemoTodo.name) }
    val selectedLayout = DashboardLayoutOption.valueOf(selectedLayoutName)
    var pinnedMemo by rememberSaveable { mutableStateOf("完成信息看板初稿") }
    var locationName by rememberSaveable { mutableStateOf("上海市") }
    var latitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var longitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var timezone by rememberSaveable { mutableStateOf("Asia/Shanghai") }
    var weather by remember { mutableStateOf(DashboardWeather()) }
    var autoRefreshEnabled by rememberSaveable { mutableStateOf(false) }
    var autoRefreshIntervalSeconds by rememberSaveable { mutableStateOf(3 * 60 * 60) }
    var nextAutoRefreshAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var hasUnsyncedDraft by rememberSaveable { mutableStateOf(false) }
    var dashboardRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    var dashboardLoading by remember { mutableStateOf(true) }
    var dashboardSaving by remember { mutableStateOf(false) }
    var dashboardMessage by remember { mutableStateOf<String?>(null) }
    var refreshInProgress by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    var preparedMediaId by rememberSaveable { mutableStateOf<String?>(null) }
    var todos by remember {
        mutableStateOf(
            listOf(
                DashboardTodo(1L, "检查 TF 存储"),
                DashboardTodo(2L, "整理 App 设置页"),
                DashboardTodo(3L, "确认电子纸布局留白"),
                DashboardTodo(4L, "准备天气服务配置"),
            ),
        )
    }

    fun applyDocument(document: DashboardDocument) {
        dashboardRevision = document.revision
        selectedLayoutName = dashboardLayoutOption(document.layoutId).name
        timezone = document.timezone
        locationName = document.cityName
        latitude = document.latitude
        longitude = document.longitude
        weather = document.weather
        autoRefreshEnabled = document.autoRefreshEnabled
        autoRefreshIntervalSeconds = document.autoRefreshIntervalSeconds
        nextAutoRefreshAt = document.nextAutoRefreshAt
        pinnedMemo = document.memo
        todos = document.todos.sortedBy { it.position }.map { DashboardTodo(it.id, it.title, it.completed) }
        hasUnsyncedDraft = false
    }
    fun reloadDashboard() {
        scope.launch {
            dashboardLoading = true
            dashboardData.load().onSuccess(::applyDocument).onFailure {
                dashboardMessage = "读取相框看板数据失败。"
            }
            dashboardLoading = false
        }
    }
    fun saveDashboard(
        layout: DashboardLayoutOption = selectedLayout,
        memo: String = pinnedMemo,
        updatedTodos: List<DashboardTodo> = todos,
        cityName: String = locationName,
        updatedLatitude: Double? = latitude,
        updatedLongitude: Double? = longitude,
        updatedTimezone: String = timezone,
        updatedAutoRefreshEnabled: Boolean = autoRefreshEnabled,
        updatedAutoRefreshIntervalSeconds: Int = autoRefreshIntervalSeconds,
    ) {
        val revision = dashboardRevision
        if (revision == null || dashboardSaving) {
            if (revision == null) dashboardMessage = "看板数据仍在加载，请稍后重试。"
            return
        }
        val document = DashboardDocument(
            revision = revision,
            layoutId = dashboardLayoutApiValue(layout),
            timezone = updatedTimezone,
            cityName = cityName,
            latitude = updatedLatitude,
            longitude = updatedLongitude,
            memo = memo,
            todos = updatedTodos.mapIndexed { index, todo -> DashboardTodoRecord(todo.id, todo.title, todo.completed, index) },
            weather = weather,
            autoRefreshEnabled = updatedAutoRefreshEnabled,
            autoRefreshIntervalSeconds = updatedAutoRefreshIntervalSeconds,
            nextAutoRefreshAt = nextAutoRefreshAt,
        )
        preparedMediaId = null
        refreshMessage = null
        scope.launch {
            dashboardSaving = true
            when (val outcome = dashboardData.save(document)) {
                is DashboardSaveResult.Saved -> {
                    applyDocument(outcome.document)
                    dashboardMessage = "已保存到相框，电子纸刷新需单独操作。"
                }
                is DashboardSaveResult.Conflict -> {
                    outcome.latest?.let(::applyDocument)
                    dashboardMessage = "相框数据已更新，已重新加载最新内容。"
                }
                is DashboardSaveResult.Failed -> dashboardMessage = "保存失败：${outcome.code}"
            }
            dashboardSaving = false
        }
    }

    LaunchedEffect(dashboardData) { reloadDashboard() }

    BackHandler(enabled = route != DashboardRoute.Overview) { route = DashboardRoute.Overview }

    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                hierarchicalPageTransition(
                    initialState == DashboardRoute.Overview && targetState != DashboardRoute.Overview,
                )
            },
            label = "dashboard-page",
        ) { displayedRoute ->
        when (displayedRoute) {
            DashboardRoute.Overview -> DashboardOverviewScreen(
                device = device,
                modeSwitchState = modeSwitchState,
                onSwitchMode = onSwitchMode,
                selectedLayout = selectedLayout,
                pinnedMemo = pinnedMemo,
                todos = todos,
                locationName = locationName,
                timezone = timezone,
                weather = weather,
                hasUnsyncedDraft = hasUnsyncedDraft,
                dashboardLoading = dashboardLoading,
                dashboardSaving = dashboardSaving,
                dashboardMessage = dashboardMessage,
                refreshInProgress = refreshInProgress,
                refreshMessage = refreshMessage,
                prepared = preparedMediaId != null,
                onReloadDashboard = ::reloadDashboard,
                onOpenLayout = { route = DashboardRoute.Layout },
                onOpenToday = { route = DashboardRoute.Today },
                onOpenWeather = { route = DashboardRoute.Weather },
                onOpenTime = { route = DashboardRoute.Time },
                onMarkRefreshRequested = {
                    if (device.activeFeature == DeviceFeature.InfoDashboard) scope.launch {
                        val preparing = preparedMediaId == null
                        refreshInProgress = true
                        refreshMessage = if (preparing) "正在生成六色画面并保存到 TF…" else "正在等待电子纸刷新…"
                        runCatching {
                            if (preparing) dashboardFrames.prepare(
                                DashboardDocument(
                                    revision = dashboardRevision ?: 0L,
                                    layoutId = dashboardLayoutApiValue(selectedLayout),
                                    timezone = timezone,
                                    cityName = locationName,
                                    latitude = latitude,
                                    longitude = longitude,
                                    memo = pinnedMemo,
                                    todos = todos.mapIndexed { index, todo ->
                                        DashboardTodoRecord(todo.id, todo.title, todo.completed, index)
                                    },
                                    weather = weather,
                                    autoRefreshEnabled = autoRefreshEnabled,
                                    autoRefreshIntervalSeconds = autoRefreshIntervalSeconds,
                                    nextAutoRefreshAt = nextAutoRefreshAt,
                                ),
                            ) else {
                                dashboardFrames.display(requireNotNull(preparedMediaId), device.modeRevision)
                                null
                            }
                        }.onSuccess { mediaId ->
                            if (preparing) {
                                preparedMediaId = requireNotNull(mediaId)
                                refreshMessage = "画面已保存到 TF，确认后可显示"
                            } else {
                                hasUnsyncedDraft = false
                                refreshMessage = "已显示到相框"
                            }
                        }.onFailure { error ->
                            refreshMessage = "看板显示失败：${error.message ?: "请检查相框连接后重试"}"
                        }
                        refreshInProgress = false
                    }
                },
            )
            DashboardRoute.Layout -> DashboardLayoutScreen(
                selected = selectedLayout,
                onBack = { route = DashboardRoute.Overview },
                onSave = { option ->
                    selectedLayoutName = option.name
                    saveDashboard(layout = option)
                    route = DashboardRoute.Overview
                },
            )
            DashboardRoute.Today -> DashboardTodayScreen(
                memo = pinnedMemo,
                todos = todos,
                onBack = { route = DashboardRoute.Overview },
                onSaveMemo = {
                    pinnedMemo = it
                    saveDashboard(memo = it)
                },
                onSaveTodos = { updated ->
                    todos = updated
                    saveDashboard(updatedTodos = updated)
                },
            )
            DashboardRoute.Weather -> DashboardWeatherScreen(
                locationName = locationName,
                latitude = latitude,
                longitude = longitude,
                timezone = timezone,
                weather = weather,
                autoRefreshEnabled = autoRefreshEnabled,
                autoRefreshIntervalSeconds = autoRefreshIntervalSeconds,
                onBack = { route = DashboardRoute.Overview },
                onSave = { city, lat, lon, zone, enabled, interval ->
                    locationName = city
                    latitude = lat
                    longitude = lon
                    timezone = zone
                    saveDashboard(
                        cityName = city,
                        updatedLatitude = lat,
                        updatedLongitude = lon,
                        updatedTimezone = zone,
                        updatedAutoRefreshEnabled = enabled,
                        updatedAutoRefreshIntervalSeconds = interval,
                    )
                    route = DashboardRoute.Overview
                },
            )
            DashboardRoute.Time -> DashboardTimeScreen(
                timezone = timezone,
                onBack = { route = DashboardRoute.Overview },
                onSaveTimezone = {
                    timezone = it
                    saveDashboard(updatedTimezone = it)
                },
            )
        }
        }
    }
}

@Composable
private fun DashboardOverviewScreen(
    device: DeviceSnapshot,
    modeSwitchState: ModeSwitchUiState,
    onSwitchMode: (DeviceFeature) -> Unit,
    selectedLayout: DashboardLayoutOption,
    pinnedMemo: String,
    todos: List<DashboardTodo>,
    locationName: String,
    timezone: String,
    weather: DashboardWeather,
    hasUnsyncedDraft: Boolean,
    dashboardLoading: Boolean,
    dashboardSaving: Boolean,
    dashboardMessage: String?,
    refreshInProgress: Boolean,
    refreshMessage: String?,
    prepared: Boolean,
    onReloadDashboard: () -> Unit,
    onOpenLayout: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenTime: () -> Unit,
    onMarkRefreshRequested: () -> Unit,
) {
    val target = DeviceFeature.InfoDashboard
    val ownsContent = device.currentContent?.ownerFeature == target
    val isActive = device.activeFeature == target
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        ModeFeatureHeader("信息看板", target, device, modeSwitchState, onSwitchMode)
        ModeSwitchStatusCard(target, modeSwitchState)
        DashboardCurrentPanel(
            device = device,
            ownsContent = ownsContent,
            layout = selectedLayout,
            memo = pinnedMemo,
            todos = todos,
            timezone = timezone,
            weather = weather,
            onChangeLayout = onOpenLayout,
        )
        if (dashboardLoading || dashboardSaving || dashboardMessage != null) {
            DashboardDeviceStatus(
                loading = dashboardLoading,
                saving = dashboardSaving,
                message = dashboardMessage,
                onReload = onReloadDashboard,
            )
        }
        DraftBanner(
            layout = selectedLayout,
            isActive = isActive,
            isRefreshing = refreshInProgress,
            message = refreshMessage,
            hasUnsyncedDraft = hasUnsyncedDraft,
            prepared = prepared,
            onRefresh = onMarkRefreshRequested,
        )
        val contentAction = if (selectedLayout == DashboardLayoutOption.WeatherDate) onOpenWeather else onOpenToday
        DashboardSummaryCard(selectedLayout, pinnedMemo, todos, locationName, weather, contentAction)
        Button(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            onClick = contentAction,
        ) {
            Icon(
                if (selectedLayout == DashboardLayoutOption.WeatherDate) Icons.Outlined.WbCloudy else Icons.Outlined.EditNote,
                contentDescription = null,
            )
            Spacer(Modifier.size(8.dp))
            Text(if (selectedLayout == DashboardLayoutOption.WeatherDate) "设置天气与日期" else "编辑今日内容")
        }
        if (selectedLayout != DashboardLayoutOption.WeatherDate) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                QuickSettingButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.CloudQueue,
                    label = "天气设置",
                    onClick = onOpenWeather,
                )
                QuickSettingButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Schedule,
                    label = "时间与时区",
                    onClick = onOpenTime,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DashboardCurrentPanel(
    device: DeviceSnapshot,
    ownsContent: Boolean,
    layout: DashboardLayoutOption,
    memo: String,
    todos: List<DashboardTodo>,
    timezone: String,
    weather: DashboardWeather,
    onChangeLayout: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CurrentDashboardFrame(
                device = device,
                ownsContent = ownsContent,
                layout = layout,
                memo = memo,
                todos = todos,
                timezone = timezone,
                weather = weather,
                modifier = Modifier.weight(1.15f),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("当前看板", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(layout.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    layout.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onChangeLayout, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("更换布局")
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun CurrentDashboardFrame(
    device: DeviceSnapshot,
    ownsContent: Boolean,
    layout: DashboardLayoutOption,
    memo: String,
    todos: List<DashboardTodo>,
    timezone: String,
    weather: DashboardWeather,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.aspectRatio(5f / 3f),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            when {
                ownsContent && device.currentContent?.kind == DeviceContentKind.ModeCover -> androidx.compose.foundation.Image(
                    painter = painterResource(DeviceFeature.InfoDashboard.modeCoverDrawableRes()),
                    contentDescription = "信息看板模式提示画面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                ownsContent && device.currentContent?.kind == DeviceContentKind.Dashboard -> EinkDashboardPreview(layout, memo, todos, timezone, weather)
                !ownsContent -> Text(
                    crossFeatureDisplayText(device.currentContent?.ownerFeature ?: device.activeFeature),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> EinkDashboardPreview(layout, memo, todos, timezone, weather)
            }
        }
    }
}

@Composable
private fun EinkDashboardPreview(
    layout: DashboardLayoutOption,
    memo: String,
    todos: List<DashboardTodo>,
    timezone: String,
    weather: DashboardWeather,
) {
    val today = remember(timezone) { runCatching { LocalDate.now(ZoneId.of(timezone)) }.getOrElse { LocalDate.now() } }
    val weekday = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[today.dayOfWeek.value - 1]
    val pending = todos.filterNot { it.completed }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFEFDF9),
        contentColor = Color(0xFF24211F),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("$weekday · ${today.monthValue}月${today.dayOfMonth}日", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when (layout) {
                DashboardLayoutOption.WeatherDate -> WeatherPreviewRow(expanded = true, weather = weather)
                DashboardLayoutOption.DateMemoTodo -> PlanPreview(memo, pending.take(3))
                DashboardLayoutOption.WeatherMemoTodo -> {
                    WeatherPreviewRow(expanded = false, weather = weather)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE6DFDA))
                    PlanPreview(memo, pending.take(2))
                }
            }
        }
    }
}

@Composable
private fun WeatherPreviewRow(expanded: Boolean, weather: DashboardWeather) {
    val today = weather.forecast.firstOrNull()
    val summary = today?.let { "${weatherName(it.weatherCode)} ${it.temperatureMaxC}°" } ?: when {
        weather.refreshing -> "更新中"
        weather.state == "waiting_location" -> "请先定位"
        weather.state == "waiting_sta" -> "等待 STA"
        weather.state == "error" -> "天气暂不可用"
        else -> "天气待同步"
    }
    val detail = today?.let { "最高 ${it.temperatureMaxC}° · 最低 ${it.temperatureMinC}°" } ?: "等待天气数据"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.WbCloudy, contentDescription = null, tint = Color(0xFF496579), modifier = Modifier.size(if (expanded) 42.dp else 28.dp))
        Column {
            Text(summary, style = if (expanded) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5E5A57))
        }
    }
}

@Composable
private fun PlanPreview(memo: String, todos: List<DashboardTodo>) {
    Text("今日重点", style = MaterialTheme.typography.labelMedium, color = Color(0xFF6C625F))
    Text(memo.ifBlank { "添加置顶备忘录" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(8.dp))
    todos.forEach { todo ->
        Text(
            text = if (todo.completed) "✓ ${todo.title}" else "□ ${todo.title}",
            style = MaterialTheme.typography.bodySmall,
            color = if (todo.completed) Color(0xFF6C625F) else Color(0xFF24211F),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DashboardDeviceStatus(
    loading: Boolean,
    saving: Boolean,
    message: String?,
    onReload: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Sync, contentDescription = null)
            Text(
                when {
                    loading -> "正在读取相框看板数据…"
                    saving -> "正在保存看板数据…"
                    else -> message.orEmpty()
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!loading && !saving) {
                TextButton(onClick = onReload) { Text("重新加载") }
            }
        }
    }
}

@Composable
private fun DraftBanner(
    layout: DashboardLayoutOption,
    isActive: Boolean,
    isRefreshing: Boolean,
    message: String?,
    hasUnsyncedDraft: Boolean,
    prepared: Boolean,
    onRefresh: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Sync, contentDescription = null)
            Text(
                message ?: when {
                    !isActive -> "切换到信息看板模式后可显示"
                    hasUnsyncedDraft -> "内容已更新，尚未显示到相框"
                    prepared -> "${layout.compactTitle}画面已保存，等待显示"
                    else -> "${layout.compactTitle}画面可以生成"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isActive && message != "已显示到相框") {
                TextButton(onClick = onRefresh, enabled = !isRefreshing) {
                    Text(if (isRefreshing) "处理中…" else if (prepared) "显示到相框" else "生成并保存")
                }
            }
        }
    }
}

@Composable
private fun DashboardSectionCard(
    icon: ImageVector,
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
            content()
        }
    }
}

@Composable
private fun DashboardSummaryCard(
    layout: DashboardLayoutOption,
    memo: String,
    todos: List<DashboardTodo>,
    locationName: String,
    weather: DashboardWeather,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当前内容", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text(if (layout == DashboardLayoutOption.WeatherDate) "设置" else "编辑") }
            }
            when (layout) {
                DashboardLayoutOption.WeatherDate -> {
                    SummaryLine(Icons.Outlined.CalendarMonth, "日期", "最近五天 · 今天居中", MaterialTheme.colorScheme.primary)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SummaryLine(Icons.Outlined.WbCloudy, "天气", weatherSummary(locationName, weather), Color(0xFF496579))
                }
                DashboardLayoutOption.DateMemoTodo -> {
                    SummaryLine(Icons.Outlined.CalendarMonth, "日期", "日期与设备时间待同步", MaterialTheme.colorScheme.primary)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SummaryLine(Icons.Outlined.EditNote, "今日重点", memo.ifBlank { "添加置顶备忘录" }, MaterialTheme.colorScheme.primary)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    val pending = todos.count { !it.completed }
                    SummaryLine(Icons.Outlined.CheckCircle, "待办", if (pending == 0) "今日待办已完成" else "$pending 项待处理", MaterialTheme.colorScheme.primary)
                }
                DashboardLayoutOption.WeatherMemoTodo -> {
                    SummaryLine(Icons.Outlined.WbCloudy, "天气", weatherSummary(locationName, weather), Color(0xFF496579))
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SummaryLine(Icons.Outlined.EditNote, "今日重点", memo.ifBlank { "添加置顶备忘录" }, MaterialTheme.colorScheme.primary)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    val pending = todos.count { !it.completed }
                    SummaryLine(Icons.Outlined.CheckCircle, "待办", if (pending == 0) "今日待办已完成" else "$pending 项待处理", MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(icon: ImageVector, label: String, value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 52.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickSettingButton(modifier: Modifier, icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(modifier = modifier.heightIn(min = 52.dp), onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardLayoutScreen(selected: DashboardLayoutOption, onBack: () -> Unit, onSave: (DashboardLayoutOption) -> Unit) {
    var selectedDraft by rememberSaveable(selected) { mutableStateOf(selected) }
    Scaffold(
        topBar = { DashboardSubpageTopBar("选择看板布局", onBack) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp),
                    onClick = { onSave(selectedDraft) },
                ) { Text("保存布局") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("选择一个最常使用的查看方式", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(DashboardLayoutOption.entries) { option ->
                val checked = option == selectedDraft
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { selectedDraft = option },
                    border = BorderStroke(1.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.outlinedCardColors(containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardLayoutMiniPreview(option, Modifier.size(width = 96.dp, height = 58.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(option.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(option.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (checked) Text("已选择", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        RadioButton(selected = checked, onClick = { selectedDraft = option })
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardLayoutMiniPreview(option: DashboardLayoutOption, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color(0xFFFEFDF9), shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, Color(0xFFE6DFDA))) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFF3A3634), MaterialTheme.shapes.extraSmall))
            when (option) {
                DashboardLayoutOption.WeatherDate -> {
                    Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFFDAE7ED), MaterialTheme.shapes.extraSmall))
                    Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFFE6DFDA), MaterialTheme.shapes.extraSmall))
                }
                DashboardLayoutOption.DateMemoTodo -> repeat(3) {
                    Box(Modifier.fillMaxWidth().height(7.dp).background(Color(0xFFE6DFDA), MaterialTheme.shapes.extraSmall))
                }
                DashboardLayoutOption.WeatherMemoTodo -> {
                    Box(Modifier.fillMaxWidth().height(9.dp).background(Color(0xFFDAE7ED), MaterialTheme.shapes.extraSmall))
                    repeat(2) { Box(Modifier.fillMaxWidth().height(7.dp).background(Color(0xFFE6DFDA), MaterialTheme.shapes.extraSmall)) }
                }
            }
        }
    }
}

@Composable
private fun DashboardTodayScreen(
    memo: String,
    todos: List<DashboardTodo>,
    onBack: () -> Unit,
    onSaveMemo: (String) -> Unit,
    onSaveTodos: (List<DashboardTodo>) -> Unit,
) {
    var editingMemo by rememberSaveable { mutableStateOf(false) }
    var addingTodo by rememberSaveable { mutableStateOf(false) }
    var editingTodoId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingTodoId by rememberSaveable { mutableStateOf<String?>(null) }
    val pending = todos.filterNot { it.completed }
    val completed = todos.filter { it.completed }

    fun move(todoId: String, direction: Int) {
        val index = todos.indexOfFirst { it.id == todoId }
        val target = index + direction
        if (index !in todos.indices || target !in todos.indices) return
        val updated = todos.toMutableList()
        val item = updated.removeAt(index)
        updated.add(target, item)
        onSaveTodos(updated)
    }

    Scaffold(
        topBar = { DashboardSubpageTopBar("备忘录与待办", onBack) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp),
                    onClick = { addingTodo = true },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("添加待办")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusCountCard("待处理", pending.size, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatusCountCard("已完成", completed.size, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                }
            }
            item {
                DashboardSectionCard(Icons.Outlined.EditNote, "置顶备忘录", "编辑", { editingMemo = true }) {
                    Text(
                        memo.ifBlank { "点击右上角添加一条置顶备忘录" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (memo.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("电子纸固定显示这一条，最多两行", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Text("待处理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            if (pending.isEmpty()) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Text("暂无待办", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else items(pending, key = { it.id }) { todo ->
                TodoEditorRow(
                    todo = todo,
                    canMoveUp = todos.indexOf(todo) > 0,
                    canMoveDown = todos.indexOf(todo) < todos.lastIndex,
                    onToggle = { onSaveTodos(todos.map { if (it.id == todo.id) it.copy(completed = true) else it }) },
                    onEdit = { editingTodoId = todo.id },
                    onDelete = { deletingTodoId = todo.id },
                    onMoveUp = { move(todo.id, -1) },
                    onMoveDown = { move(todo.id, 1) },
                )
            }
            item {
                val shown = 3
                if (pending.size > shown) Text(
                    "电子纸显示前 $shown 项，其余概括为“还有 ${pending.size - shown} 项待办”。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (completed.isNotEmpty()) {
                item { Text("已完成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(completed, key = { "completed-${it.id}" }) { todo ->
                    TodoEditorRow(
                        todo = todo,
                        canMoveUp = false,
                        canMoveDown = false,
                        onToggle = { onSaveTodos(todos.map { if (it.id == todo.id) it.copy(completed = false) else it }) },
                        onEdit = { editingTodoId = todo.id },
                        onDelete = { deletingTodoId = todo.id },
                        onMoveUp = {},
                        onMoveDown = {},
                    )
                }
            }
        }
    }
    if (editingMemo) TextEditDialog("编辑今日重点", memo, "保存", onDismiss = { editingMemo = false }) {
        onSaveMemo(it)
        editingMemo = false
    }
    if (addingTodo) TextEditDialog("添加待办", "", "添加", onDismiss = { addingTodo = false }) { title ->
        if (title.isNotBlank()) onSaveTodos(todos + DashboardTodo(System.currentTimeMillis(), title.trim()))
        addingTodo = false
    }
    editingTodoId?.let { id ->
        val todo = todos.firstOrNull { it.id == id }
        if (todo == null) editingTodoId = null else TextEditDialog(
            "编辑待办", todo.title, "保存", onDismiss = { editingTodoId = null },
        ) { title ->
            if (title.isNotBlank()) onSaveTodos(todos.map { if (it.id == id) it.copy(title = title.trim()) else it })
            editingTodoId = null
        }
    }
    deletingTodoId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingTodoId = null },
            title = { Text("删除待办？") },
            text = { Text("删除后将同时从信息看板数据中移除。") },
            confirmButton = {
                TextButton(onClick = {
                    onSaveTodos(todos.filterNot { it.id == id })
                    deletingTodoId = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingTodoId = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun StatusCountCard(label: String, count: Int, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, color = tint, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TodoEditorRow(
    todo: DashboardTodo,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = todo.completed, onCheckedChange = { onToggle() })
            Text(
                todo.title,
                modifier = Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (todo.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!todo.completed) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(Icons.Outlined.KeyboardArrowUp, "上移") }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(Icons.Outlined.KeyboardArrowDown, "下移") }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "删除") }
        }
    }
}

@Composable
private fun DashboardWeatherScreen(
    locationName: String,
    latitude: Double?,
    longitude: Double?,
    timezone: String,
    weather: DashboardWeather,
    autoRefreshEnabled: Boolean,
    autoRefreshIntervalSeconds: Int,
    onBack: () -> Unit,
    onSave: (String, Double?, Double?, String, Boolean, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cityDraft by rememberSaveable(locationName) { mutableStateOf(locationName) }
    var timezoneDraft by rememberSaveable(timezone) { mutableStateOf(timezone) }
    var latitudeDraft by rememberSaveable(latitude) { mutableStateOf(latitude) }
    var longitudeDraft by rememberSaveable(longitude) { mutableStateOf(longitude) }
    var autoRefreshDraft by rememberSaveable(autoRefreshEnabled) { mutableStateOf(autoRefreshEnabled) }
    var intervalDraft by rememberSaveable(autoRefreshIntervalSeconds) { mutableStateOf(autoRefreshIntervalSeconds) }
    var locating by rememberSaveable { mutableStateOf(false) }
    var locationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var editingLocation by rememberSaveable { mutableStateOf(false) }
    var editingTimezone by rememberSaveable { mutableStateOf(false) }

    fun locate() {
        locating = true
        locationMessage = null
        requestSingleLocation(context) { result ->
            result.onSuccess { location ->
                latitudeDraft = location.latitude
                longitudeDraft = location.longitude
                scope.launch {
                    cityDraft = reverseGeocodeCity(context, location) ?: cityDraft
                    locationMessage = if (cityDraft == locationName) "已获取当前位置" else "已定位到 $cityDraft"
                    locating = false
                }
            }.onFailure {
                locationMessage = "定位失败，请检查系统定位或手动填写城市"
                locating = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) locate()
        else locationMessage = "未获得定位权限，可手动填写城市"
    }
    fun requestLocation() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) locate() else permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
        )
    }

    Scaffold(topBar = { DashboardSubpageTopBar("天气与日期", onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("日期预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                FiveDayDateStrip(timezoneDraft)
            }
            item {
                Text("三日天气", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                ThreeDayWeather(weather, cityDraft)
            }
            item {
                Text("位置与更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    val coordinateText = if (latitudeDraft != null && longitudeDraft != null) {
                        String.format(Locale.US, "%s · %.4f, %.4f", cityDraft, latitudeDraft, longitudeDraft)
                    } else cityDraft
                    SettingsRowContent(Icons.Outlined.LocationOn, "当前地点", coordinateText, "修改") { editingLocation = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRowContent(Icons.Outlined.Schedule, "时区", timezoneDraft, "修改") { editingTimezone = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("自动刷新电子纸") },
                        supportingContent = { Text("刷新前自动获取最新天气，仅在信息看板模式执行") },
                        leadingContent = { Icon(Icons.Outlined.Sync, contentDescription = null) },
                        trailingContent = {
                            Switch(checked = autoRefreshDraft, onCheckedChange = { autoRefreshDraft = it })
                        },
                    )
                    if (autoRefreshDraft) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("刷新间隔", style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(3, 6, 12, 24).forEach { hours ->
                                    val seconds = hours * 60 * 60
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = { intervalDraft = seconds },
                                        border = BorderStroke(
                                            1.dp,
                                            if (intervalDraft == seconds) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                                        ),
                                    ) { Text("${hours}h") }
                                }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    enabled = !locating,
                    onClick = ::requestLocation,
                ) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (locating) "正在获取位置…" else "使用手机当前位置")
                }
                locationMessage?.let {
                    Text(it, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    enabled = cityDraft.isNotBlank() && timezoneDraft.isNotBlank(),
                    onClick = {
                        onSave(
                            cityDraft.trim(), latitudeDraft, longitudeDraft, timezoneDraft.trim(),
                            autoRefreshDraft, intervalDraft,
                        )
                    },
                ) {
                    Text("保存设置")
                }
                Text(
                    "保存不会立即刷新。自动刷新到点后先更新天气；失败时保留旧画面并稍后重试。",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (editingLocation) TextEditDialog("设置地点", cityDraft, "保存", onDismiss = { editingLocation = false }) {
        if (it.isNotBlank()) {
            cityDraft = it.trim()
            latitudeDraft = null
            longitudeDraft = null
        }
        editingLocation = false
    }
    if (editingTimezone) TextEditDialog("设置时区", timezoneDraft, "保存", onDismiss = { editingTimezone = false }) {
        if (it.isNotBlank()) timezoneDraft = it.trim()
        editingTimezone = false
    }
}

@Composable
private fun FiveDayDateStrip(timezone: String) {
    val today = remember(timezone) {
        runCatching { LocalDate.now(ZoneId.of(timezone)) }.getOrElse { LocalDate.now() }
    }
    val formatter = remember { DateTimeFormatter.ofPattern("MM/dd") }
    val weekdays = remember { listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (-2..2).forEach { offset ->
                val date = today.plusDays(offset.toLong())
                val isToday = offset == 0
                Surface(
                    modifier = Modifier.weight(1f),
                    color = if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(date.format(formatter), style = MaterialTheme.typography.labelLarge, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium)
                        Text(weekdays[date.dayOfWeek.value - 1], style = MaterialTheme.typography.bodySmall)
                        Text(if (isToday) "今天" else " ", style = MaterialTheme.typography.labelSmall, color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreeDayWeather(weather: DashboardWeather, cityName: String) {
    val labels = listOf("今天", "明天", "后天")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        labels.forEachIndexed { index, label ->
            val day = weather.forecast.getOrNull(index)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(label, modifier = Modifier.widthIn(min = 40.dp), style = MaterialTheme.typography.titleSmall)
                Icon(Icons.Outlined.WbCloudy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    day?.let { "${weatherName(it.weatherCode)}  ${it.temperatureMaxC}° / ${it.temperatureMinC}°" }
                        ?: weatherStatusText(weather),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (index < 2) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Text(
            "$cityName · ${if (weather.forecast.isNotEmpty()) "每小时自动更新" else weatherStatusText(weather)}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun weatherStatusText(weather: DashboardWeather): String = when {
    weather.refreshing -> "正在更新天气"
    weather.state == "waiting_location" -> "请先使用手机定位"
    weather.state == "waiting_sta" -> "等待相框连接 STA"
    weather.state == "error" -> "天气更新失败，保留旧数据"
    else -> "天气待同步"
}

private fun weatherSummary(cityName: String, weather: DashboardWeather): String =
    weather.forecast.firstOrNull()?.let {
        "$cityName · ${weatherName(it.weatherCode)} · ${it.temperatureMaxC}° / ${it.temperatureMinC}°"
    } ?: "$cityName · ${weatherStatusText(weather)}"

@Composable
private fun SettingsRowContent(icon: ImageVector, title: String, summary: String, action: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(enabled = action.isNotBlank(), onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            if (action.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

@SuppressLint("MissingPermission")
private fun requestSingleLocation(context: Context, onResult: (Result<Location>) -> Unit) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = when {
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        else -> return onResult(Result.failure(IllegalStateException("location disabled")))
    }
    var completed = false
    lateinit var listener: LocationListener
    fun complete(result: Result<Location>) {
        if (completed) return
        completed = true
        manager.removeUpdates(listener)
        onResult(result)
    }
    listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            complete(Result.success(location))
        }
        override fun onProviderDisabled(provider: String) {
            complete(Result.failure(IllegalStateException("provider disabled")))
        }
        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    runCatching { manager.requestSingleUpdate(provider, listener, Looper.getMainLooper()) }
        .onFailure { complete(Result.failure(it)) }
    Handler(Looper.getMainLooper()).postDelayed(
        { complete(Result.failure(IllegalStateException("location timeout"))) },
        15_000,
    )
}

private suspend fun reverseGeocodeCity(context: Context, location: Location): String? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale.SIMPLIFIED_CHINESE)
            .getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()
            ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull()
}

@Composable
private fun DashboardTimeScreen(timezone: String, onBack: () -> Unit, onSaveTimezone: (String) -> Unit) {
    var editingTimezone by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { DashboardSubpageTopBar("时间与时区", onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsRow(Icons.Outlined.Schedule, "当前设备时间", "等待设备 RTC 数据", "", {})
            SettingsRow(Icons.Outlined.CalendarMonth, "时区", timezone, "修改", { editingTimezone = true })
            DashboardSectionCard(Icons.Outlined.Sync, "校时", "", {}) {
                Text("使用手机时间校准", style = MaterialTheme.typography.titleMedium)
                Text("校时将更新设备 RTC 与看板 revision；是否刷新电子纸由当前模式决定。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(modifier = Modifier.padding(top = 4.dp), onClick = {}) { Text("使用手机时间校准") }
            }
        }
    }
    if (editingTimezone) TextEditDialog("设置时区", timezone, "保存", onDismiss = { editingTimezone = false }) {
        if (it.isNotBlank()) onSaveTimezone(it.trim())
        editingTimezone = false
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, summary: String, action: String, onClick: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().clickable(enabled = action.isNotBlank(), onClick = onClick)) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
            leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = {
                if (action.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardSubpageTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回信息看板") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun TextEditDialog(title: String, initialValue: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(if (title.contains("地点")) "城市或经纬度" else "内容") },
                modifier = Modifier.fillMaxWidth(),
                minLines = if (title.contains("重点")) 2 else 1,
                maxLines = if (title.contains("重点")) 2 else 3,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun InfoDashboardHostPreview() = EInkPhotoTheme {
    InfoDashboardHost(
        contentPadding = PaddingValues(),
        device = DeviceSnapshot(
            deviceId = "preview",
            displayName = "演示相框",
            isDemo = true,
            connection = com.einkphoto.app.core.device.DeviceConnectionState.Offline,
            activeFeature = DeviceFeature.LocalAlbum,
            displayBusy = false,
            storageFreeBytes = null,
            capabilities = null,
        ),
        modeSwitchState = ModeSwitchUiState(),
        onSwitchMode = {},
    )
}
