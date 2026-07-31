package com.einkphoto.app.ui.aialbum

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.einkphoto.app.ui.components.pressFeedbackClickable
import com.einkphoto.app.ui.theme.EInkPhotoTheme

internal enum class AiServiceMode(val label: String) {
    Gateway("AI Gateway"),
    Direct("官方直连"),
}

internal data class AiConfigSnapshot(
    val configured: Boolean,
    val enabled: Boolean,
    val mode: AiServiceMode,
    val provider: String,
    val serviceUrl: String,
    val chatModel: String,
    val imageModel: String,
    val apiKeySuffix: String?,
    val lastVerifiedLabel: String?,
) {
    companion object {
        fun unconfigured() = AiConfigSnapshot(
            configured = false,
            enabled = false,
            mode = AiServiceMode.Gateway,
            provider = "火山方舟",
            serviceUrl = "",
            chatModel = "",
            imageModel = "",
            apiKeySuffix = null,
            lastVerifiedLabel = null,
        )
    }
}

internal class AiConfigDraft(
    val mode: AiServiceMode,
    val provider: String,
    val serviceUrl: String,
    val chatModel: String,
    val imageModel: String,
    val newApiKey: String,
) {
    override fun toString(): String = "AiConfigDraft(mode=$mode, provider=$provider, serviceUrl=$serviceUrl, chatModel=$chatModel, imageModel=$imageModel, newApiKey=[REDACTED])"
}

internal enum class AiFieldState { Valid, Invalid, NotChecked }

internal data class AiFieldCheck(val label: String, val state: AiFieldState, val detail: String)

internal fun maskApiKeySuffix(suffix: String?): String? = suffix
    ?.trim()
    ?.takeIf { it.length == 4 }
    ?.let { "••••••••$it" }

internal fun validateAiConfigDraft(draft: AiConfigDraft, hasSavedCredential: Boolean): List<AiFieldCheck> {
    val urlValid = draft.serviceUrl.startsWith("https://") || draft.serviceUrl.startsWith("http://")
    val keyValid = draft.mode == AiServiceMode.Gateway || hasSavedCredential || draft.newApiKey.length >= 8
    return listOf(
        AiFieldCheck("服务商", if (draft.provider.isNotBlank()) AiFieldState.Valid else AiFieldState.Invalid, if (draft.provider.isNotBlank()) "已选择" else "请选择服务商"),
        AiFieldCheck("服务地址", if (urlValid) AiFieldState.Valid else AiFieldState.Invalid, if (urlValid) "格式正确" else "请输入以 http:// 或 https:// 开头的地址"),
        AiFieldCheck("对话模型", if (draft.chatModel.isNotBlank()) AiFieldState.Valid else AiFieldState.Invalid, if (draft.chatModel.isNotBlank()) "已填写" else "请填写对话模型或接入点 ID"),
        AiFieldCheck("生图模型", if (draft.imageModel.isNotBlank()) AiFieldState.Valid else AiFieldState.Invalid, if (draft.imageModel.isNotBlank()) "已填写" else "请填写生图模型或接入点 ID"),
        AiFieldCheck("API Key", if (keyValid) AiFieldState.Valid else AiFieldState.Invalid, when {
            draft.mode == AiServiceMode.Gateway -> "由 Gateway 管理"
            hasSavedCredential && draft.newApiKey.isBlank() -> "保留设备中已保存的 Key"
            keyValid -> "已填写新的 Key"
            else -> "官方直连需要填写新的 API Key"
        }),
    )
}

@Composable
internal fun AiModelConfigScreen(
    snapshot: AiConfigSnapshot,
    onBack: () -> Unit,
    newApiKey: String,
    onNewApiKeyChange: (String) -> Unit,
    tutorialCurrentStep: Int,
    tutorialCompletedSteps: Int,
    onOpenTutorial: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var modeName by rememberSaveable { mutableStateOf(snapshot.mode.name) }
    var serviceUrl by rememberSaveable { mutableStateOf(snapshot.serviceUrl) }
    var chatModel by rememberSaveable { mutableStateOf(snapshot.chatModel) }
    var imageModel by rememberSaveable {
        mutableStateOf(snapshot.imageModel.ifBlank { "Doubao-Seedream-4.0" })
    }
    var keyVisible by remember { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var checks by remember { mutableStateOf<List<AiFieldCheck>>(emptyList()) }
    var actionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    val selectedMode = AiServiceMode.entries.firstOrNull { it.name == modeName } ?: AiServiceMode.Gateway

    fun currentDraft() = AiConfigDraft(
        mode = selectedMode,
        provider = snapshot.provider,
        serviceUrl = serviceUrl.trim(),
        chatModel = chatModel.trim(),
        imageModel = imageModel.trim(),
        newApiKey = newApiKey,
    )

    fun validateAndExplain(testRequested: Boolean) {
        checks = validateAiConfigDraft(currentDraft(), snapshot.configured && snapshot.apiKeySuffix != null)
        actionMessage = if (checks.any { it.state == AiFieldState.Invalid }) {
            "请先补充或修正标记的配置项。"
        } else if (testRequested) {
            "服务尚未接入：配置没有保存，也没有执行服务测试，本次不会产生费用。"
        } else {
            "服务尚未接入：配置没有保存。"
        }
    }

    Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 720.dp)) {
            ConfigHeader(onBack)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { ServiceStatusCard(snapshot) }
                item {
                    SectionTitle("服务模式")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiServiceMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { modeName = mode.name; actionMessage = null },
                                label = { Text(mode.label) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    Text(
                        if (selectedMode == AiServiceMode.Gateway) "推荐：长期密钥由安全 Gateway 保管。" else "原型直连：新的 Key 只允许写入，不会从相框取回。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    SectionTitle("模型信息")
                    OutlinedTextField(
                        value = snapshot.provider,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("服务商") },
                        supportingText = { Text("第一版使用火山方舟") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = serviceUrl,
                        onValueChange = { serviceUrl = it; actionMessage = null },
                        label = { Text("服务 URL") },
                        supportingText = { Text("填写控制台或 Gateway 提供的完整地址") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = chatModel,
                        onValueChange = { chatModel = it; actionMessage = null },
                        label = { Text("对话模型 / 接入点 ID") },
                        supportingText = { Text("用于小智日常文字对话") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = imageModel,
                        onValueChange = { imageModel = it; actionMessage = null },
                        label = { Text("生图模型 / 接入点 ID") },
                        supportingText = { Text("例如 Doubao-Seedream-4.0 对应的接入点") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    SectionTitle("API Key")
                    OutlinedTextField(
                        value = newApiKey,
                        onValueChange = { onNewApiKeyChange(it); actionMessage = null },
                        enabled = selectedMode == AiServiceMode.Direct,
                        label = { Text(if (snapshot.configured) "输入新的 API Key（可选）" else "输入 API Key") },
                        supportingText = {
                            Text(
                                when {
                                    selectedMode == AiServiceMode.Gateway -> "Gateway 模式不在相框中保存服务商 Key"
                                    snapshot.configured -> "当前已配置 ${maskApiKeySuffix(snapshot.apiKeySuffix) ?: "Key"}；留空表示不修改"
                                    else -> "默认隐藏；保存后只显示尾四位"
                                },
                            )
                        },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }, enabled = selectedMode == AiServiceMode.Direct) {
                                Icon(if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (keyVisible) "隐藏 API Key" else "显示 API Key")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth().animateContentSize(tween(220))) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().pressFeedbackClickable(role = Role.Button, onClick = { advancedExpanded = !advancedExpanded }).padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("高级设置", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Icon(if (advancedExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (advancedExpanded) "收起" else "展开")
                            }
                            if (advancedExpanded) {
                                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusLine("请求超时", "使用推荐设置")
                                    Text("第一版不开放复杂采样参数，避免因误配置导致请求失败或额外费用。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                item {
                    ModelTutorialEntryCard(
                        currentStep = tutorialCurrentStep,
                        completedSteps = tutorialCompletedSteps,
                        onClick = onOpenTutorial,
                    )
                }
                if (checks.isNotEmpty()) item { ValidationResults(checks) }
                item {
                    actionMessage?.let {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Text(it, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.size(10.dp))
                    }
                    Button(onClick = { validateAndExplain(testRequested = true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null)
                        Spacer(Modifier.size(8.dp))
                        Text("保存并测试")
                    }
                    OutlinedButton(onClick = { validateAndExplain(testRequested = false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Save, null)
                        Spacer(Modifier.size(8.dp))
                        Text("仅保存")
                    }
                }
                item {
                    HorizontalDivider()
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        enabled = snapshot.configured,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, null)
                        Spacer(Modifier.size(6.dp))
                        Text("删除 AI 配置", color = if (snapshot.configured) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "删除配置不会删除已经生成的 AI 图片和用量记录。",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("删除 AI 配置？") },
            text = { Text("删除后将无法使用 AI 对话和图片生成。已生成的 AI 图片和用量记录不会删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        actionMessage = "服务尚未接入，当前配置没有删除。"
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消") } },
        )
    }
}

@Composable
private fun ConfigHeader(onBack: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text("AI 模型配置", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ServiceStatusCard(snapshot: AiConfigSnapshot) {
    val statusTitle = when {
        !snapshot.configured -> "AI 服务未配置"
        snapshot.enabled -> "AI 服务已配置"
        else -> "AI 服务已停用"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (snapshot.configured) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = if (snapshot.configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(statusTitle, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleLarge)
            }
            StatusLine("服务模式", snapshot.mode.label)
            StatusLine("服务商", snapshot.provider.ifBlank { "未填写" })
            StatusLine("对话模型", snapshot.chatModel.ifBlank { "未填写" })
            StatusLine("生图模型", snapshot.imageModel.ifBlank { "未填写" })
            StatusLine("API Key", maskApiKeySuffix(snapshot.apiKeySuffix) ?: "未配置")
            StatusLine("最近验证", snapshot.lastVerifiedLabel ?: "尚未验证")
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.padding(start = 16.dp).weight(1f))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun ModelTutorialEntryCard(
    currentStep: Int,
    completedSteps: Int,
    onClick: () -> Unit,
) {
    val normalizedStep = normalizeTutorialStep(currentStep)
    val normalizedCompleted = completedSteps.coerceIn(0, MODEL_TUTORIAL_STEP_COUNT)
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .pressFeedbackClickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(MODEL_TUTORIAL_TITLE, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("7 个步骤 · 约 3 分钟", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "打开模型配置教程")
            }
            LinearProgressIndicator(
                progress = { tutorialProgress(normalizedCompleted) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "当前：第 ${normalizedStep + 1} 步 · 已完成 $normalizedCompleted/$MODEL_TUTORIAL_STEP_COUNT",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ValidationResults(checks: List<AiFieldCheck>) {
    val invalidFields = checks.filter { it.state == AiFieldState.Invalid }
    val fieldCheck = AiFieldCheck(
        label = "字段",
        state = if (invalidFields.isEmpty()) AiFieldState.Valid else AiFieldState.Invalid,
        detail = if (invalidFields.isEmpty()) "必填信息完整" else "请检查：${invalidFields.joinToString("、") { it.label }}",
    )
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("检查结果", style = MaterialTheme.typography.titleMedium)
            ValidationLine(fieldCheck)
            ValidationLine(AiFieldCheck("STA", AiFieldState.NotChecked, "尚未检查"))
            ValidationLine(AiFieldCheck("互联网", AiFieldState.NotChecked, "尚未检查"))
            ValidationLine(AiFieldCheck("鉴权", AiFieldState.NotChecked, "尚未验证"))
            ValidationLine(AiFieldCheck("对话模型", AiFieldState.NotChecked, "尚未验证"))
            ValidationLine(AiFieldCheck("生图模型", AiFieldState.NotChecked, "尚未验证"))
        }
    }
}

@Composable
private fun ValidationLine(check: AiFieldCheck) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when (check.state) {
                AiFieldState.Valid -> Icons.Outlined.CheckCircle
                AiFieldState.Invalid -> Icons.Outlined.ErrorOutline
                AiFieldState.NotChecked -> Icons.Outlined.Wifi
            },
            contentDescription = null,
            tint = when (check.state) {
                AiFieldState.Valid -> MaterialTheme.colorScheme.primary
                AiFieldState.Invalid -> MaterialTheme.colorScheme.error
                AiFieldState.NotChecked -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(check.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(check.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(name = "AI 配置 · 未配置小屏", showBackground = true, widthDp = 320, heightDp = 720)
@Composable
private fun AiModelConfigEmptyPreview() = EInkPhotoTheme(darkTheme = false) {
    AiModelConfigScreen(AiConfigSnapshot.unconfigured(), {}, "", {}, 0, 0, {}, PaddingValues())
}

@Preview(name = "AI 配置 · 已配置深色大字体", showBackground = true, widthDp = 393, heightDp = 852, fontScale = 1.5f)
@Composable
private fun AiModelConfigConfiguredPreview() = EInkPhotoTheme(darkTheme = true) {
    AiModelConfigScreen(
        snapshot = AiConfigSnapshot(
            configured = true,
            enabled = true,
            mode = AiServiceMode.Direct,
            provider = "火山方舟",
            serviceUrl = "https://example.invalid/api",
            chatModel = "doubao-chat-endpoint",
            imageModel = "seedream-endpoint",
            apiKeySuffix = "A7K9",
            lastVerifiedLabel = "2026-07-31 14:30",
        ),
        onBack = {},
        newApiKey = "",
        onNewApiKeyChange = {},
        tutorialCurrentStep = 3,
        tutorialCompletedSteps = 3,
        onOpenTutorial = {},
        contentPadding = PaddingValues(),
    )
}

@Preview(name = "AI 配置 · 横屏", showBackground = true, widthDp = 720, heightDp = 360)
@Composable
private fun AiModelConfigLandscapePreview() = EInkPhotoTheme(darkTheme = false) {
    AiModelConfigScreen(AiConfigSnapshot.unconfigured(), {}, "", {}, 1, 1, {}, PaddingValues())
}
