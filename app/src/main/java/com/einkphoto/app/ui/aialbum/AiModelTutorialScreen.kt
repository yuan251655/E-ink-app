package com.einkphoto.app.ui.aialbum

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.ModelTraining
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.einkphoto.app.ui.components.AppleModalBottomSheet as ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.einkphoto.app.ui.components.pressFeedbackClickable
import com.einkphoto.app.ui.theme.EInkPhotoTheme

internal const val MODEL_TUTORIAL_STEP_COUNT = 7
internal const val MODEL_TUTORIAL_TITLE = "模型配置教程"

internal data class ModelTutorialStep(
    val title: String,
    val description: String,
    val whereToFind: String,
    val commonIssue: String,
    val icon: ImageVector,
)

internal val modelTutorialSteps = listOf(
    ModelTutorialStep(
        title = "登录火山引擎",
        description = "打开火山引擎官网并登录账号。首次使用时，先按平台要求完成注册和实名认证。",
        whereToFind = "火山引擎官网 → 右上角“登录”",
        commonIssue = "看不到控制台入口时，请先确认账号已经登录，并完成平台要求的账号认证。",
        icon = Icons.Outlined.AccountCircle,
    ),
    ModelTutorialStep(
        title = "开通火山方舟",
        description = "进入火山方舟控制台，按页面提示开通模型服务。",
        whereToFind = "控制台 → 产品与服务 → 火山方舟",
        commonIssue = "首次进入可能需要确认服务协议、账户信息或计费方式，请以控制台当前提示为准。",
        icon = Icons.Outlined.Cloud,
    ),
    ModelTutorialStep(
        title = "启用需要的模型",
        description = "启用一个对话模型，并启用 Doubao-Seedream-4.0 等图片生成模型。",
        whereToFind = "火山方舟 → 模型广场 / 模型列表 → 选择模型 → 开通",
        commonIssue = "模型名称和可用区域可能变化，请以控制台当前显示为准。生图模型与对话模型需要分别确认。",
        icon = Icons.Outlined.ModelTraining,
    ),
    ModelTutorialStep(
        title = "创建 API Key",
        description = "在 API Key 管理页创建新 Key。创建完成后立即复制，并保存在安全位置。",
        whereToFind = "火山方舟 → API Key 管理 → 创建 API Key → 复制",
        commonIssue = "完整 Key 通常只显示一次。不要截图分享，也不要把完整 Key 写入日志或诊断信息。",
        icon = Icons.Outlined.Key,
    ),
    ModelTutorialStep(
        title = "复制模型与服务信息",
        description = "复制对话模型 ID、生图模型 ID 或推理接入点 ID，并确认服务地址。",
        whereToFind = "在线推理 / 推理接入点 → 对应模型 → 复制接入点 ID；服务地址见接口说明",
        commonIssue = "不要把模型展示名称当成接入点 ID；复制后请核对前后是否带有空格。",
        icon = Icons.Outlined.ContentCopy,
    ),
    ModelTutorialStep(
        title = "回到 App 填写",
        description = "选择服务模式，填写服务地址、对话模型和生图模型；官方直连时再输入新的 API Key。",
        whereToFind = "AI 相册 → 模型配置 → 模型信息 / API Key",
        commonIssue = "App 不会从相框取回旧 Key。输入框留空表示不修改已经保存的 Key。",
        icon = Icons.Outlined.Api,
    ),
    ModelTutorialStep(
        title = "保存并验证",
        description = "检查字段后保存配置，再验证网络、鉴权、对话模型和生图模型状态。",
        whereToFind = "模型配置页面底部 → 保存并测试",
        commonIssue = "真实验证可能产生少量费用，App 必须先明确提示并取得确认；当前接口未接入时不会伪造成功。",
        icon = Icons.Outlined.CheckCircle,
    ),
)

internal fun normalizeTutorialStep(index: Int): Int = index.coerceIn(0, MODEL_TUTORIAL_STEP_COUNT - 1)

internal fun tutorialProgress(completedCount: Int): Float =
    completedCount.coerceIn(0, MODEL_TUTORIAL_STEP_COUNT).toFloat() / MODEL_TUTORIAL_STEP_COUNT

internal fun nextTutorialStep(index: Int): Int = normalizeTutorialStep(index + 1)

internal fun previousTutorialStep(index: Int): Int = normalizeTutorialStep(index - 1)

internal fun toggleTutorialStep(completedSteps: Set<Int>, index: Int): Set<Int> {
    val normalized = normalizeTutorialStep(index)
    return if (normalized in completedSteps) completedSteps - normalized else completedSteps + normalized
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiModelTutorialScreen(
    currentStep: Int,
    completedSteps: Set<Int>,
    onCurrentStepChange: (Int) -> Unit,
    onCompletedStepsChange: (Set<Int>) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val normalizedStep = normalizeTutorialStep(currentStep)
    val step = modelTutorialSteps[normalizedStep]
    var commonIssueExpanded by rememberSaveable(normalizedStep) { mutableStateOf(false) }
    var showAllSteps by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.fillMaxSize().widthIn(max = 760.dp)) {
            val landscape = maxWidth > maxHeight
            Column(Modifier.fillMaxSize()) {
                TutorialHeader(onBack = onBack)
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "第 ${normalizedStep + 1} / $MODEL_TUTORIAL_STEP_COUNT 步",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                LinearProgressIndicator(
                                    progress = { (normalizedStep + 1f) / MODEL_TUTORIAL_STEP_COUNT },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            TextButton(
                                onClick = { showAllSteps = true },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Outlined.FormatListNumbered, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("全部步骤")
                            }
                        }
                    }
                    item {
                        if (landscape) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TutorialGoalCard(normalizedStep, step, Modifier.weight(0.9f))
                                TutorialDetails(step, commonIssueExpanded, { commonIssueExpanded = !commonIssueExpanded }, Modifier.weight(1.1f))
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                TutorialGoalCard(normalizedStep, step)
                                TutorialDetails(step, commonIssueExpanded, { commonIssueExpanded = !commonIssueExpanded })
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                onCompletedStepsChange(toggleTutorialStep(completedSteps, normalizedStep))
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        ) {
                            Icon(
                                if (normalizedStep in completedSteps) Icons.Outlined.CheckCircle else Icons.Outlined.Check,
                                contentDescription = null,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(if (normalizedStep in completedSteps) "已完成这一步" else "标记为已完成")
                        }
                    }
                    if (landscape) {
                        item {
                            TutorialFooter(
                                currentStep = normalizedStep,
                                onPrevious = { onCurrentStepChange(previousTutorialStep(normalizedStep)) },
                                onNext = {
                                    onCompletedStepsChange(completedSteps + normalizedStep)
                                    if (normalizedStep == MODEL_TUTORIAL_STEP_COUNT - 1) onFinish()
                                    else onCurrentStepChange(nextTutorialStep(normalizedStep))
                                },
                            )
                        }
                    }
                }
                if (!landscape) {
                    TutorialFooter(
                        currentStep = normalizedStep,
                        onPrevious = { onCurrentStepChange(previousTutorialStep(normalizedStep)) },
                        onNext = {
                            onCompletedStepsChange(completedSteps + normalizedStep)
                            if (normalizedStep == MODEL_TUTORIAL_STEP_COUNT - 1) onFinish()
                            else onCurrentStepChange(nextTutorialStep(normalizedStep))
                        },
                    )
                }
            }
        }
    }

    if (showAllSteps) {
        ModalBottomSheet(onDismissRequest = { showAllSteps = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(MODEL_TUTORIAL_TITLE, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "进度仅保存在当前 App 页面，不会写入相框或云端。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { tutorialProgress(completedSteps.size) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(modelTutorialSteps, key = { index, _ -> index }) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .pressFeedbackClickable(role = Role.Button) {
                                    onCurrentStepChange(index)
                                    showAllSteps = false
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                if (index in completedSteps) Icons.Outlined.CheckCircle else item.icon,
                                contentDescription = null,
                                tint = if (index in completedSteps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(Modifier.weight(1f)) {
                                Text("${index + 1}. ${item.title}", fontWeight = if (index == normalizedStep) FontWeight.SemiBold else FontWeight.Normal)
                                if (index == normalizedStep) Text("当前步骤", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                        }
                        if (index != modelTutorialSteps.lastIndex) HorizontalDivider()
                    }
                }
                TextButton(
                    onClick = {
                        showAllSteps = false
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("跳过并退出教程")
                }
            }
        }
    }
}

@Composable
private fun TutorialHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回模型配置")
        }
        Text(MODEL_TUTORIAL_TITLE, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TutorialGoalCard(index: Int, step: ModelTutorialStep, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StepBadge(index + 1)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Icon(step.icon, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(step.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(step.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TutorialDetails(
    step: ModelTutorialStep,
    commonIssueExpanded: Boolean,
    onToggleCommonIssue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("在哪里找 / 复制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(step.whereToFind, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth().animateContentSize()) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .pressFeedbackClickable(role = Role.Button, onClick = onToggleCommonIssue)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("常见问题", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(
                        if (commonIssueExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (commonIssueExpanded) "收起常见问题" else "展开常见问题",
                    )
                }
                if (commonIssueExpanded) {
                    Text(
                        step.commonIssue,
                        modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepBadge(number: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TutorialFooter(currentStep: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onPrevious,
            enabled = currentStep > 0,
            modifier = Modifier.weight(1f).heightIn(min = 50.dp),
        ) {
            Text("上一步")
        }
        Button(onClick = onNext, modifier = Modifier.weight(1.4f).heightIn(min = 50.dp)) {
            Text(if (currentStep == MODEL_TUTORIAL_STEP_COUNT - 1) "完成并返回配置" else "下一步")
        }
    }
}

@Preview(name = "模型配置教程 浅色", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AiModelTutorialLightPreview() = EInkPhotoTheme(darkTheme = false) {
    AiModelTutorialScreen(2, setOf(0, 1), {}, {}, {}, {}, PaddingValues())
}

@Preview(name = "模型配置教程 深色大字体", showBackground = true, widthDp = 393, heightDp = 852, fontScale = 1.5f)
@Composable
private fun AiModelTutorialDarkLargePreview() = EInkPhotoTheme(darkTheme = true) {
    AiModelTutorialScreen(3, setOf(0, 1, 2), {}, {}, {}, {}, PaddingValues())
}

@Preview(name = "模型配置教程 横屏", showBackground = true, widthDp = 720, heightDp = 360)
@Composable
private fun AiModelTutorialLandscapePreview() = EInkPhotoTheme(darkTheme = false) {
    AiModelTutorialScreen(4, setOf(0, 1, 2, 3), {}, {}, {}, {}, PaddingValues())
}
