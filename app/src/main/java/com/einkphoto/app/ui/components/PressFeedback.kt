package com.einkphoto.app.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch

/** One app-wide press treatment for Material buttons, chips, switches and custom click targets. */
@Composable
fun rememberApplePressIndication(accent: Color): Indication = remember(accent) {
    ApplePressIndication(accent)
}

private data class ApplePressIndication(private val accent: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        ApplePressIndicationNode(interactionSource, accent)
}

private class ApplePressIndicationNode(
    private val interactionSource: InteractionSource,
    private val accent: Color,
): Modifier.Node(), DrawModifierNode {
    private val pressScale = Animatable(1f)
    private val overlayAlpha = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                val pressed = interaction is PressInteraction.Press
                if (pressed || interaction is PressInteraction.Release || interaction is PressInteraction.Cancel) {
                    launch {
                        pressScale.animateTo(
                            targetValue = if (pressed) 0.97f else 1f,
                            animationSpec = if (pressed) tween(70) else spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                    }
                    launch {
                        overlayAlpha.animateTo(
                            targetValue = if (pressed) 0.055f else 0f,
                            animationSpec = tween(if (pressed) 70 else 160),
                        )
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val contentScope = this
        withTransform({ scale(pressScale.value, pressScale.value, center) }) { contentScope.drawContent() }
        if (overlayAlpha.value > 0f) drawRect(accent.copy(alpha = overlayAlpha.value))
    }
}

/** Custom rows already own click semantics; their motion now comes from the global indication. */
@Composable
fun Modifier.pressFeedbackClickable(
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        enabled = enabled,
        role = role,
        onClick = onClick,
    )
}

/** Shared, interruptible right-in/right-out hierarchy transition for every feature host. */
fun hierarchicalPageTransition(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return (fadeIn(tween(180)) + slideInHorizontally(tween(320)) { direction * it / 8 }) togetherWith
        (fadeOut(tween(150)) + slideOutHorizontally(tween(250)) { -direction * it / 10 })
}
