package com.mu.workbench.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.mu.workbench.data.ThemeStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 主题级全局按压与点击特效 IndicationNodeFactory：
 * 为 9 种主题分别提供按压/松手态的视觉反馈。
 */
data class MuThemeIndication(val mu: MuColors) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return MuThemeIndicationNode(interactionSource, mu)
    }
}

private class MuThemeIndicationNode(
    private val interactionSource: InteractionSource,
    private val mu: MuColors
) : Modifier.Node(), DrawModifierNode {

    private val pressProgress = Animatable(0f)
    private val burstProgress = Animatable(1f)
    private var pressPos: Offset = Offset.Unspecified
    private var pressJob: Job? = null
    private var burstJob: Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressPos = interaction.pressPosition
                        pressJob?.cancel()
                        pressJob = coroutineScope.launch {
                            pressProgress.animateTo(1f, tween(85)) { invalidateDraw() }
                        }
                        burstJob?.cancel()
                        burstJob = coroutineScope.launch {
                            burstProgress.snapTo(0f)
                            invalidateDraw()
                            burstProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
                            ) {
                                invalidateDraw()
                            }
                        }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        pressJob?.cancel()
                        pressJob = coroutineScope.launch {
                            pressProgress.animateTo(0f, tween(170)) { invalidateDraw() }
                        }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val p = pressProgress.value
        val b = burstProgress.value
        val burstActive = b > 0.001f && b < 0.999f
        if (p <= 0.001f && !burstActive) return
        if (size.width <= 1f || size.height <= 1f) return

        val fade = if (burstActive) (1f - b).coerceIn(0f, 1f) else 0f
        val origin = if (pressPos.isSpecified) {
            Offset(pressPos.x.coerceIn(0f, size.width), pressPos.y.coerceIn(0f, size.height))
        } else {
            center
        }
        val maxR = size.maxDimension.coerceAtLeast(16f) * 1.15f

        when (mu.style) {
            ThemeStyle.P5R -> {
                if (p > 0.001f) {
                    drawRect(color = mu.accent.copy(alpha = 0.24f * p))
                    val strokeW = 2.dp.toPx()
                    drawRect(
                        color = Color.White.copy(alpha = 0.85f * p),
                        topLeft = Offset(strokeW / 2f, strokeW / 2f),
                        size = Size((size.width - strokeW).coerceAtLeast(1f), (size.height - strokeW).coerceAtLeast(1f)),
                        style = Stroke(strokeW)
                    )
                }
                if (burstActive) {
                    val x = (-0.25f + 1.55f * b) * size.width
                    val slashW = (size.width * 0.30f).coerceAtLeast(22f)
                    val skew = size.height * 0.35f
                    val slashPath = Path().apply {
                        moveTo(x - slashW * 0.5f + skew, 0f)
                        lineTo(x + slashW * 0.5f + skew, 0f)
                        lineTo(x + slashW * 0.5f - skew, size.height)
                        lineTo(x - slashW * 0.5f - skew, size.height)
                        close()
                    }
                    drawPath(slashPath, Color.White.copy(alpha = 0.44f * fade))
                    val trailPath = Path().apply {
                        moveTo(x - slashW * 0.9f + skew, 0f)
                        lineTo(x - slashW * 0.55f + skew, 0f)
                        lineTo(x - slashW * 0.55f - skew, size.height)
                        lineTo(x - slashW * 0.9f - skew, size.height)
                        close()
                    }
                    drawPath(trailPath, mu.accent.copy(alpha = 0.38f * fade))
                }
            }

            ThemeStyle.P3R -> {
                if (p > 0.001f) {
                    drawRect(color = mu.p3Cyan.copy(alpha = 0.20f * p))
                    val strokeW = 1.6.dp.toPx()
                    drawRect(
                        color = mu.p3Cyan.copy(alpha = 0.78f * p),
                        topLeft = Offset(strokeW / 2f, strokeW / 2f),
                        size = Size((size.width - strokeW).coerceAtLeast(1f), (size.height - strokeW).coerceAtLeast(1f)),
                        style = Stroke(strokeW)
                    )
                }
                if (burstActive) {
                    val r1 = (b * maxR).coerceAtLeast(2f)
                    val r2 = ((b * 0.72f) * maxR).coerceAtLeast(2f)
                    val bloom = Brush.radialGradient(
                        colors = listOf(mu.p3Cyan.copy(alpha = 0.30f * fade), Color.Transparent),
                        center = origin,
                        radius = r1
                    )
                    drawCircle(brush = bloom, radius = r1, center = origin)
                    drawCircle(
                        color = mu.p3Cyan.copy(alpha = 0.65f * fade),
                        radius = r1,
                        center = origin,
                        style = Stroke(1.8.dp.toPx())
                    )
                    drawCircle(
                        color = mu.p3Pink.copy(alpha = 0.48f * fade),
                        radius = r2,
                        center = origin,
                        style = Stroke(1.2.dp.toPx())
                    )
                }
            }

            ThemeStyle.IOS -> {
                val baseGlow = if (mu.isDark) Color.White else mu.accent
                if (p > 0.001f) {
                    drawRect(color = baseGlow.copy(alpha = 0.13f * p))
                }
                if (burstActive) {
                    val r = (b * maxR).coerceAtLeast(2f)
                    val bloom = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.32f * fade),
                            baseGlow.copy(alpha = 0.16f * fade),
                            Color.Transparent
                        ),
                        center = origin,
                        radius = r
                    )
                    drawCircle(brush = bloom, radius = r, center = origin)
                }
            }

            ThemeStyle.MINIMAL -> {
                if (p > 0.001f) {
                    drawRect(color = mu.ink.copy(alpha = 0.06f * p))
                }
            }

            ThemeStyle.GLASS -> {
                if (p > 0.001f) {
                    drawRect(color = Color.White.copy(alpha = (if (mu.isDark) 0.10f else 0.18f) * p))
                }
                if (burstActive) {
                    val r = (b * maxR).coerceAtLeast(2f)
                    val bloom = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.38f * fade), Color.Transparent),
                        center = origin,
                        radius = r
                    )
                    drawCircle(brush = bloom, radius = r, center = origin)
                    val strokeW = 1.dp.toPx()
                    drawRect(
                        color = Color.White.copy(alpha = 0.6f * fade),
                        topLeft = Offset(strokeW / 2f, strokeW / 2f),
                        size = Size((size.width - strokeW).coerceAtLeast(1f), (size.height - strokeW).coerceAtLeast(1f)),
                        style = Stroke(strokeW)
                    )
                }
            }

            ThemeStyle.BENTO -> {
                if (p > 0.001f) {
                    drawRect(color = mu.accent.copy(alpha = 0.10f * p))
                }
                if (burstActive) {
                    val r = (b * maxR).coerceAtLeast(2f)
                    val bloom = Brush.radialGradient(
                        colors = listOf(mu.accent.copy(alpha = 0.18f * fade), Color.Transparent),
                        center = origin,
                        radius = r
                    )
                    drawCircle(brush = bloom, radius = r, center = origin)
                }
            }

            ThemeStyle.BRUTAL -> {
                if (p > 0.001f) {
                    drawRect(color = mu.ink.copy(alpha = 0.10f * p))
                }
                if (burstActive) {
                    drawRect(color = mu.accent2.copy(alpha = 0.25f * fade))
                }
            }

            ThemeStyle.CLAY -> {
                if (p > 0.001f) {
                    val shadeBrush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, mu.hardShadowColor.copy(alpha = 0.35f * p)),
                        startY = 0f,
                        endY = size.height
                    )
                    drawRect(brush = shadeBrush)
                    drawRect(
                        color = Color.White.copy(alpha = 0.15f * p),
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height * 0.35f)
                    )
                }
                if (burstActive) {
                    val r = (b * maxR).coerceAtLeast(2f)
                    val bloom = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.25f * fade), Color.Transparent),
                        center = origin,
                        radius = r
                    )
                    drawCircle(brush = bloom, radius = r, center = origin)
                }
            }

            ThemeStyle.OLED -> {
                if (p > 0.001f) {
                    drawRect(color = mu.accent.copy(alpha = 0.10f * p))
                }
                if (burstActive) {
                    val r = (b * maxR).coerceAtLeast(2f)
                    val bloom = Brush.radialGradient(
                        colors = listOf(mu.accent.copy(alpha = 0.20f * fade), Color.Transparent),
                        center = origin,
                        radius = r
                    )
                    drawCircle(brush = bloom, radius = r, center = origin)
                    drawCircle(
                        color = mu.accent.copy(alpha = 0.55f * fade),
                        radius = r,
                        center = origin,
                        style = Stroke(1.5.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * 物理弹跳按压变形（缩放/压扁/斜移），仅使用 graphicsLayer 合成层，零重排。
 */
fun Modifier.muPressTransform(
    interactionSource: InteractionSource,
    enabled: Boolean = true
): Modifier = composed {
    val style = LocalMuColors.current.style
    val pressed by interactionSource.collectIsPressedAsState()
    val spec = when (style) {
        ThemeStyle.P5R -> spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 860f)
        ThemeStyle.P3R -> spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 720f)
        ThemeStyle.IOS -> spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 650f)
        ThemeStyle.MINIMAL -> spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f)
        ThemeStyle.GLASS -> spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 650f)
        ThemeStyle.BENTO -> spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 700f)
        ThemeStyle.BRUTAL -> spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1200f)
        ThemeStyle.CLAY -> spring<Float>(dampingRatio = 0.45f, stiffness = 520f)
        ThemeStyle.OLED -> spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f)
    }

    val targetScaleX = if (pressed && enabled) {
        when (style) {
            ThemeStyle.P5R -> 0.93f
            ThemeStyle.P3R -> 0.945f
            ThemeStyle.IOS -> 0.96f
            ThemeStyle.MINIMAL -> 0.97f
            ThemeStyle.GLASS -> 0.96f
            ThemeStyle.BENTO -> 0.95f
            ThemeStyle.BRUTAL -> 1f
            ThemeStyle.CLAY -> 1.035f
            ThemeStyle.OLED -> 0.97f
        }
    } else 1f
    val targetScaleY = if (pressed && enabled) {
        when (style) {
            ThemeStyle.CLAY -> 0.93f
            else -> targetScaleX
        }
    } else 1f
    val targetDx = if (pressed && enabled && style == ThemeStyle.BRUTAL) 2.5f else 0f
    val targetDy = if (pressed && enabled) {
        when (style) {
            ThemeStyle.BRUTAL -> 2.5f
            ThemeStyle.P5R -> 1.5f
            else -> 0f
        }
    } else 0f
    val targetRot = if (pressed && enabled && style == ThemeStyle.P5R) -0.7f else 0f

    val sx by animateFloatAsState(targetValue = targetScaleX, animationSpec = spec, label = "muPressSx")
    val sy by animateFloatAsState(targetValue = targetScaleY, animationSpec = spec, label = "muPressSy")
    val dx by animateFloatAsState(targetValue = targetDx, animationSpec = spec, label = "muPressDx")
    val dy by animateFloatAsState(targetValue = targetDy, animationSpec = spec, label = "muPressDy")
    val rot by animateFloatAsState(targetValue = targetRot, animationSpec = spec, label = "muPressRot")

    this.graphicsLayer {
        scaleX = sx
        scaleY = sy
        translationX = dx * density
        translationY = dy * density
        rotationZ = rot
    }
}

/**
 * 组合物理弹跳 + 主题点击高光特效 + 轻触觉震动的可点击修饰符。
 */
fun Modifier.muTactileClickable(
    enabled: Boolean = true,
    haptic: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val hapticFb = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    this
        .muPressTransform(interactionSource = interactionSource, enabled = enabled)
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled
        ) {
            if (haptic) {
                hapticFb.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            onClick()
        }
}
