package com.einkphoto.app.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.einkphoto.app.R
import com.einkphoto.app.core.device.DeviceContentKind
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceSnapshot
import com.einkphoto.app.feature.mode.ModeSwitchUiState
import com.einkphoto.app.ui.components.ModeFeatureHeader
import com.einkphoto.app.ui.components.ModeSwitchStatusCard
import com.einkphoto.app.ui.components.crossFeatureDisplayText
import com.einkphoto.app.ui.components.modeCoverDrawableRes
import com.einkphoto.app.ui.theme.EInkPhotoTheme
import kotlinx.coroutines.launch

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
    var timezone by rememberSaveable { mutableStateOf("Asia/Shanghai") }
    var hasUnsyncedDraft by rememberSaveable { mutableStateOf(false) }
    var dashboardRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    var dashboardLoading by remember { mutableStateOf(true) }
    var dashboardSaving by remember { mutableStateOf(false) }
    var dashboardMessage by remember { mutableStateOf<String?>(null) }
    var refreshInProgress by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
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
        pinnedMemo = document.memo
        todos = document.todos.sortedBy { it.position }.map { DashboardTodo(it.id, it.title, it.completed) }
        hasUnsyncedDraft = false
    }
    fun reloadDashboard() {
        scope.launch {
            dashboardLoading = true
            dashboardData.load().onSuccess(::applyDocument).onFailure {
                dashboardMessage = "Unable to read device dashboard data."
            }
            dashboardLoading = false
        }
    }
    fun saveDashboard() {
        val revision = dashboardRevision
        if (revision == null || dashboardSaving) {
            if (revision == null) dashboardMessage = "Dashboard data is still loading."
            return
        }
        val document = DashboardDocument(
            revision = revision,
            layoutId = dashboardLayoutApiValue(selectedLayout),
            timezone = timezone,
            cityName = locationName,
            memo = pinnedMemo,
            todos = todos.mapIndexed { index, todo -> DashboardTodoRecord(todo.id, todo.title, todo.completed, index) },
        )
        scope.launch {
            dashboardSaving = true
            when (val outcome = dashboardData.save(document)) {
                is DashboardSaveResult.Saved -> {
                    applyDocument(outcome.document)
                    dashboardMessage = "Saved to the frame. Refresh remains a separate action."
                }
                is DashboardSaveResult.Conflict -> {
                    outcome.latest?.let(::applyDocument)
                    dashboardMessage = "Device data changed; latest data was reloaded."
                }
                is DashboardSaveResult.Failed -> dashboardMessage = "Save failed: ${outcome.code}"
            }
            dashboardSaving = false
        }
    }

    LaunchedEffect(dashboardData) { reloadDashboard() }

    BackHandler(enabled = route != DashboardRoute.Overview) { route = DashboardRoute.Overview }

    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        when (route) {
            DashboardRoute.Overview -> DashboardOverviewScreen(
                device = device,
                modeSwitchState = modeSwitchState,
                onSwitchMode = onSwitchMode,
                selectedLayout = selectedLayout,
                pinnedMemo = pinnedMemo,
                todos = todos,
                locationName = locationName,
                hasUnsyncedDraft = hasUnsyncedDraft,
                dashboardLoading = dashboardLoading,
                dashboardSaving = dashboardSaving,
                dashboardMessage = dashboardMessage,
                refreshInProgress = refreshInProgress,
                refreshMessage = refreshMessage,
                onReloadDashboard = ::reloadDashboard,
                onOpenLayout = { route = DashboardRoute.Layout },
                onOpenToday = { route = DashboardRoute.Today },
                onOpenWeather = { route = DashboardRoute.Weather },
                onOpenTime = { route = DashboardRoute.Time },
                onMarkRefreshRequested = {
                    if (device.activeFeature == DeviceFeature.InfoDashboard) scope.launch {
                        refreshInProgress = true
                        refreshMessage = "正在上传并等待电子纸刷新…"
                        runCatching {
                            dashboardFrames.uploadAndDisplay(selectedLayout.name, pinnedMemo,
                                todos.filterNot { it.completed }.map { it.title }, device.modeRevision)
                        }.onSuccess {
                            hasUnsyncedDraft = false
                            refreshMessage = "已显示到相框"
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
                    saveDashboard()
                    route = DashboardRoute.Overview
                },
            )
            DashboardRoute.Today -> DashboardTodayScreen(
                memo = pinnedMemo,
                todos = todos,
                onBack = { route = DashboardRoute.Overview },
                onSaveMemo = {
                    pinnedMemo = it
                    saveDashboard()
                },
                onToggleTodo = { id ->
                    todos = todos.map { todo -> if (todo.id == id) todo.copy(completed = !todo.completed) else todo }
                    saveDashboard()
                },
                onAddTodo = { title ->
                    todos = todos + DashboardTodo(System.currentTimeMillis(), title)
                    saveDashboard()
                },
            )
            DashboardRoute.Weather -> DashboardWeatherScreen(
                locationName = locationName,
                onBack = { route = DashboardRoute.Overview },
                onSaveLocation = {
                    locationName = it
                    saveDashboard()
                },
            )
            DashboardRoute.Time -> DashboardTimeScreen(
                timezone = timezone,
                onBack = { route = DashboardRoute.Overview },
                onSaveTimezone = {
                    timezone = it
                    saveDashboard()
                },
            )
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
    hasUnsyncedDraft: Boolean,
    dashboardLoading: Boolean,
    dashboardSaving: Boolean,
    dashboardMessage: String?,
    refreshInProgress: Boolean,
    refreshMessage: String?,
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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        ModeFeatureHeader("信息看板", target, device, modeSwitchState, onSwitchMode)
        ModeSwitchStatusCard(target, modeSwitchState)
        Text("相框当前画面", style = MaterialTheme.typography.titleMedium)
        CurrentDashboardFrame(device, ownsContent, selectedLayout, pinnedMemo, todos)
        if (dashboardLoading || dashboardSaving || dashboardMessage != null) {
            DashboardDeviceStatus(
                loading = dashboardLoading,
                saving = dashboardSaving,
                message = dashboardMessage,
                onReload = onReloadDashboard,
            )
        }
        if (hasUnsyncedDraft || refreshMessage != null) {
            DraftBanner(
                isActive = isActive,
                isRefreshing = refreshInProgress,
                message = refreshMessage,
                onRefresh = onMarkRefreshRequested,
            )
        }
        DashboardSectionCard(
            icon = Icons.Outlined.Dashboard,
            title = "当前布局",
            actionLabel = "更换",
            onAction = onOpenLayout,
        ) {
            Text(selectedLayout.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(selectedLayout.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DashboardSummaryCard(pinnedMemo, todos, locationName, onOpenToday)
        Button(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            onClick = onOpenToday,
        ) {
            Icon(Icons.Outlined.EditNote, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("编辑今日内容")
        }
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
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CurrentDashboardFrame(
    device: DeviceSnapshot,
    ownsContent: Boolean,
    layout: DashboardLayoutOption,
    memo: String,
    todos: List<DashboardTodo>,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().aspectRatio(5f / 3f),
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
                ownsContent && device.currentContent?.kind == DeviceContentKind.Dashboard -> EinkDashboardPreview(layout, memo, todos)
                !ownsContent -> Text(
                    crossFeatureDisplayText(device.currentContent?.ownerFeature ?: device.activeFeature),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> EinkDashboardPreview(layout, memo, todos)
            }
        }
    }
}

@Composable
private fun EinkDashboardPreview(layout: DashboardLayoutOption, memo: String, todos: List<DashboardTodo>) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFEFDF9),
        contentColor = Color(0xFF24211F),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("周三 · 8月5日", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when (layout) {
                DashboardLayoutOption.WeatherDate -> WeatherPreviewRow(expanded = true)
                DashboardLayoutOption.DateMemoTodo -> PlanPreview(memo, todos.take(3))
                DashboardLayoutOption.WeatherMemoTodo -> {
                    WeatherPreviewRow(expanded = false)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE6DFDA))
                    PlanPreview(memo, todos.take(2))
                }
            }
        }
    }
}

@Composable
private fun WeatherPreviewRow(expanded: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.WbCloudy, contentDescription = null, tint = Color(0xFF496579), modifier = Modifier.size(if (expanded) 42.dp else 28.dp))
        Column {
            Text(if (expanded) "多云  28°" else "多云 28°", style = if (expanded) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (expanded) "最高 31° · 最低 25°" else "更新于 10:00", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5E5A57))
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
                    loading -> "Loading device dashboard data…"
                    saving -> "Saving dashboard data…"
                    else -> message.orEmpty()
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!loading && !saving) {
                TextButton(onClick = onReload) { Text("Reload") }
            }
        }
    }
}

@Composable
private fun DraftBanner(
    isActive: Boolean,
    isRefreshing: Boolean,
    message: String?,
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
                message ?: if (isActive) "内容已更新，尚未显示到相框" else "内容已更新，切换到信息看板后显示",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isActive && message != "已显示到相框") {
                TextButton(onClick = onRefresh, enabled = !isRefreshing) {
                    Text(if (isRefreshing) "刷新中…" else "刷新看板")
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
private fun DashboardSummaryCard(memo: String, todos: List<DashboardTodo>, locationName: String, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("今日摘要", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text("编辑") }
            }
            SummaryLine(Icons.Outlined.WbCloudy, "天气", "$locationName · 多云 28° · 更新于 10:00", Color(0xFF496579))
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SummaryLine(Icons.Outlined.EditNote, "今日重点", memo.ifBlank { "添加置顶备忘录" }, MaterialTheme.colorScheme.primary)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val pending = todos.count { !it.completed }
            SummaryLine(Icons.Outlined.CheckCircle, "待办", if (pending == 0) "今日待办已完成" else "$pending 项待处理", MaterialTheme.colorScheme.primary)
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
    onToggleTodo: (String) -> Unit,
    onAddTodo: (String) -> Unit,
) {
    var editingMemo by rememberSaveable { mutableStateOf(false) }
    var addingTodo by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { DashboardSubpageTopBar("今日内容", onBack) },
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
                DashboardSectionCard(Icons.Outlined.EditNote, "今日重点", "编辑", { editingMemo = true }) {
                    Text(memo.ifBlank { "添加置顶备忘录" }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("电子纸最多显示两行", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Text("待办", style = MaterialTheme.typography.titleMedium) }
            items(todos, key = { it.id }) { todo ->
                OutlinedCard(modifier = Modifier.fillMaxWidth().clickable { onToggleTodo(todo.id) }) {
                    ListItem(
                        headlineContent = { Text(todo.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            Icon(
                                if (todo.completed) Icons.Outlined.CheckCircle else Icons.Outlined.CalendarMonth,
                                contentDescription = if (todo.completed) "已完成" else "待处理",
                                tint = if (todo.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
            item {
                if (todos.size > 3) Text("电子纸将显示前 3 项，其余以“还有 ${todos.size - 3} 项待办”概括。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (editingMemo) TextEditDialog("编辑今日重点", memo, "保存", onDismiss = { editingMemo = false }) {
        onSaveMemo(it)
        editingMemo = false
    }
    if (addingTodo) TextEditDialog("添加待办", "", "添加", onDismiss = { addingTodo = false }) { title ->
        if (title.isNotBlank()) onAddTodo(title.trim())
        addingTodo = false
    }
}

@Composable
private fun DashboardWeatherScreen(locationName: String, onBack: () -> Unit, onSaveLocation: (String) -> Unit) {
    var editingLocation by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { DashboardSubpageTopBar("天气设置", onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsRow(Icons.Outlined.LocationOn, "当前地点", "$locationName · 来自手机定位", "修改", { editingLocation = true })
            }
            item {
                SettingsRow(Icons.Outlined.Schedule, "更新频率", "每小时检查一次", "更改", {})
            }
            item {
                DashboardSectionCard(Icons.Outlined.WbCloudy, "天气状态", "", {}) {
                    Text("上次更新 10:00 · 多云 28°", style = MaterialTheme.typography.bodyLarge)
                    Text("设备同步待接入；上线后将显示真实缓存和网络状态。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = {}) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("手动刷新天气")
                }
                Text("不足五分钟时将显示等待时间；刷新天气不会自动刷新电子纸。", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (editingLocation) TextEditDialog("设置地点", locationName, "保存", onDismiss = { editingLocation = false }) {
        if (it.isNotBlank()) onSaveLocation(it.trim())
        editingLocation = false
    }
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
