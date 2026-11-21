package com.mu.workbench.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mu.workbench.data.AccentStyle
import com.mu.workbench.data.ThemeStyle

@Immutable
data class MuColors(
    val style: ThemeStyle,
    val isDark: Boolean,
    val accent: Color,
    val accent2: Color,
    val accent3: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val paper: Color,
    val paper2: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val line2: Color,
    val chrome: Color,
    val chrome2: Color,
    val chromeInk: Color,
    val chromeSoft: Color,
    val p3Cyan: Color,
    val p3Pink: Color,
    val p3Deep: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
    val hardShadowColor: Color,
    val cardRadius: Dp,
    val cardBorderWidth: Dp,
    val hardShadowDp: Dp
)

val LocalMuColors = staticCompositionLocalOf {
    buildMuColors(ThemeStyle.P5R, AccentStyle.BLUE, false)
}

fun buildMuColors(style: ThemeStyle, accentStyle: AccentStyle, isDark: Boolean): MuColors {
    val (acc, acc2, acc3) = when (accentStyle) {
        AccentStyle.BLUE -> when (style) {
            ThemeStyle.IOS -> Triple(Color(0xFF007AFF), Color(0xFF5AC8FA), Color(0xFF0056B3))
            ThemeStyle.P3R -> Triple(Color(0xFF015FCC), Color(0xFF16CFFB), Color(0xFF014CA3))
            ThemeStyle.P5R -> Triple(Color(0xFFE4002B), Color(0xFFFF3355), Color(0xFFB3001F))
        }
        AccentStyle.RED -> Triple(Color(0xFFE4002B), Color(0xFFFF5C70), Color(0xFFB3001F))
        AccentStyle.CYAN -> Triple(Color(0xFF0D7F86), Color(0xFF14B8A6), Color(0xFF09575C))
        AccentStyle.VIOLET -> Triple(Color(0xFF5B32C9), Color(0xFF8B5CF6), Color(0xFF3E208E))
        AccentStyle.MINT -> Triple(Color(0xFF0F7A4F), Color(0xFF22C55E), Color(0xFF0A5436))
        AccentStyle.SUNSET -> Triple(Color(0xFFD94A1E), Color(0xFFF59E0B), Color(0xFF9C3211))
        AccentStyle.ROSE -> Triple(Color(0xFFC2185B), Color(0xFFFB7185), Color(0xFF8A0E40))
        AccentStyle.P3BLUE -> Triple(Color(0xFF015FCC), Color(0xFF16CFFB), Color(0xFF014CA3))
    }

    val p3Cyan = Color(0xFF16CFFB)
    val p3Pink = Color(0xFFFD77D9)
    val p3Deep = Color(0xFF031F64)

    return when (style) {
        ThemeStyle.IOS -> if (!isDark) {
            MuColors(
                style = style, isDark = false,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = acc, accentSoft = acc.copy(alpha = 0.13f),
                paper = Color(0xFFF2F2F7), paper2 = Color(0xFFE5E5EA),
                surface1 = Color.White, surface2 = Color(0xFFF9F9FB), surface3 = Color(0xFFEEEEF4),
                ink = Color(0xFF1C1C1E), ink2 = Color(0xFF3A3A3C), ink3 = Color(0xFF8E8E93),
                line = Color(0x1C3C3C43), line2 = Color(0x2E3C3C43),
                chrome = Color(0xF5FAFAFC), chrome2 = Color(0xFFFFFFFF), chromeInk = Color(0xFF1C1C1E), chromeSoft = Color(0xFF6C6C70),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF34C759), warn = Color(0xFFFF9500), danger = Color(0xFFFF3B30),
                hardShadowColor = Color.Transparent,
                cardRadius = 18.dp, cardBorderWidth = 0.75.dp, hardShadowDp = 0.dp
            )
        } else {
            MuColors(
                style = style, isDark = true,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = acc2, accentSoft = acc.copy(alpha = 0.22f),
                paper = Color(0xFF000000), paper2 = Color(0xFF1C1C1E),
                surface1 = Color(0xFF1C1C1E), surface2 = Color(0xFF2C2C2E), surface3 = Color(0xFF3A3A3C),
                ink = Color(0xFFFFFFFF), ink2 = Color(0xD1EBEBF5), ink3 = Color(0xFF8E8E93),
                line = Color(0x1EFFFFFF), line2 = Color(0x30FFFFFF),
                chrome = Color(0xF51C1C1E), chrome2 = Color(0xFF2C2C2E), chromeInk = Color(0xFFFFFFFF), chromeSoft = Color(0xFF98989D),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF30D158), warn = Color(0xFFFF9F0A), danger = Color(0xFFFF453A),
                hardShadowColor = Color.Transparent,
                cardRadius = 18.dp, cardBorderWidth = 0.75.dp, hardShadowDp = 0.dp
            )
        }

        ThemeStyle.P3R -> if (!isDark) {
            MuColors(
                style = style, isDark = false,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = acc3, accentSoft = acc.copy(alpha = 0.12f),
                paper = Color(0xFFEEF4FD), paper2 = Color(0xFFE2ECFB),
                surface1 = Color.White, surface2 = Color(0xFFF6FAFF), surface3 = Color(0xFFE9F1FE),
                ink = Color(0xFF031F64), ink2 = Color(0xFF27467E), ink3 = Color(0xFF5D6A88),
                line = Color(0xFFCBD9F1), line2 = Color(0x2E031F64),
                chrome = Color(0xFF03164A), chrome2 = Color(0xFF015FCC), chromeInk = Color.White, chromeSoft = Color(0xFFB8EAFF),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF057A4E), warn = Color(0xFFB45309), danger = Color(0xFFCF1F36),
                hardShadowColor = Color(0x1C031F64),
                cardRadius = 7.dp, cardBorderWidth = 1.dp, hardShadowDp = 3.dp
            )
        } else {
            MuColors(
                style = style, isDark = true,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = acc2, accentSoft = acc.copy(alpha = 0.24f),
                paper = Color(0xFF020B20), paper2 = Color(0xFF051530),
                surface1 = Color(0xFF0A1E42), surface2 = Color(0xFF0D2450), surface3 = Color(0xFF10295A),
                ink = Color.White, ink2 = Color(0xFFCFE2FF), ink3 = Color(0xFF8DABDA),
                line = Color(0xFF26457E), line2 = Color(0x428CC3FF),
                chrome = Color(0xFF03164A), chrome2 = Color(0xFF0148A6), chromeInk = Color.White, chromeSoft = Color(0xFFB8EAFF),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF4BE3A2), warn = Color(0xFFFFC24D), danger = Color(0xFFFF5C74),
                hardShadowColor = Color(0x80000412),
                cardRadius = 7.dp, cardBorderWidth = 1.dp, hardShadowDp = 3.dp
            )
        }

        ThemeStyle.P5R -> if (!isDark) {
            MuColors(
                style = style, isDark = false,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = acc3, accentSoft = acc.copy(alpha = 0.12f),
                paper = Color(0xFFF7F4EC), paper2 = Color(0xFFECE7DA),
                surface1 = Color.White, surface2 = Color(0xFFFAF8F2), surface3 = Color(0xFFE5DFD0),
                ink = Color(0xFF0B0B0C), ink2 = Color(0xFF2C2C30), ink3 = Color(0xFF6B6860),
                line = Color(0xFF0B0B0C), line2 = Color(0x4D0B0B0C),
                chrome = Color(0xFF0B0B0C), chrome2 = Color(0xFF19191D), chromeInk = Color(0xFFF7F4EC), chromeSoft = Color(0xFFC4BEB0),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF0F7A4F), warn = Color(0xFFB45309), danger = Color(0xFFC1121F),
                hardShadowColor = Color(0xFF0B0B0C),
                cardRadius = 0.dp, cardBorderWidth = 2.dp, hardShadowDp = 4.dp
            )
        } else {
            MuColors(
                style = style, isDark = true,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = acc2, accentSoft = acc.copy(alpha = 0.22f),
                paper = Color(0xFF0B0B0C), paper2 = Color(0xFF17171A),
                surface1 = Color(0xFF141416), surface2 = Color(0xFF19191D), surface3 = Color(0xFF232329),
                ink = Color(0xFFF2EFE6), ink2 = Color(0xFFC9C4B8), ink3 = Color(0xFF918C82),
                line = Color(0xFFF2EFE6), line2 = Color(0x57F2EFE6),
                chrome = Color(0xFF0B0B0C), chrome2 = Color(0xFF17171A), chromeInk = Color(0xFFF2EFE6), chromeSoft = Color(0xFF9E988D),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF22C55E), warn = Color(0xFFF59E0B), danger = Color(0xFFEF4444),
                hardShadowColor = acc,
                cardRadius = 0.dp, cardBorderWidth = 2.dp, hardShadowDp = 3.dp
            )
        }
    }
}

object MuMotion {
    fun intOffsetSpring(style: ThemeStyle): FiniteAnimationSpec<IntOffset> = when (style) {
        // 临界阻尼高刚度弹簧：约 260ms 顺滑推入，无慢速衰减尾迹
        ThemeStyle.IOS -> spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 620f
        )
        ThemeStyle.P3R -> tween(durationMillis = 190, easing = FastOutSlowInEasing)
        ThemeStyle.P5R -> tween(durationMillis = 160, easing = FastOutSlowInEasing)
    }

    fun floatSpring(style: ThemeStyle): FiniteAnimationSpec<Float> = when (style) {
        ThemeStyle.IOS -> spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 620f
        )
        ThemeStyle.P3R -> tween(durationMillis = 190, easing = FastOutSlowInEasing)
        ThemeStyle.P5R -> tween(durationMillis = 160, easing = FastOutSlowInEasing)
    }

    /**
     * 零拖影切屏动画：
     * - iOS：仿 UINavigationController / 分页器纯不透明整页推动（上层完全不透明覆盖下层微视差），杜绝双层文字重影；
     * - P5R：160ms 利落硬切微移，无慢速半透明混叠；
     * - P3R：190ms 干脆滑入，快速退场。
     */
    fun <T> tabTransition(
        style: ThemeStyle,
        direction: Int // >0 forward, <0 backward, 0 fade
    ): AnimatedContentTransitionScope<T>.() -> ContentTransform = {
        val spec = intOffsetSpring(style)
        when {
            style == ThemeStyle.IOS && direction > 0 -> {
                ContentTransform(
                    targetContentEnter = slideInHorizontally(animationSpec = spec) { full -> full },
                    initialContentExit = slideOutHorizontally(animationSpec = spec) { full -> -(full * 0.25f).toInt() },
                    targetContentZIndex = 1f,
                    sizeTransform = SizeTransform(clip = true)
                )
            }
            style == ThemeStyle.IOS && direction < 0 -> {
                ContentTransform(
                    targetContentEnter = slideInHorizontally(animationSpec = spec) { full -> -(full * 0.25f).toInt() },
                    initialContentExit = slideOutHorizontally(animationSpec = spec) { full -> full },
                    targetContentZIndex = -1f,
                    sizeTransform = SizeTransform(clip = true)
                )
            }
            style == ThemeStyle.P5R -> {
                val shift = if (direction > 0) 80 else if (direction < 0) -80 else 0
                ContentTransform(
                    targetContentEnter = slideInHorizontally(tween(150, easing = FastOutSlowInEasing)) { shift } +
                        fadeIn(tween(80, easing = FastOutSlowInEasing)),
                    initialContentExit = fadeOut(tween(50)),
                    targetContentZIndex = 1f,
                    sizeTransform = SizeTransform(clip = true)
                )
            }
            style == ThemeStyle.P3R -> {
                val shift = if (direction > 0) 96 else if (direction < 0) -96 else 36
                ContentTransform(
                    targetContentEnter = slideInHorizontally(tween(180, easing = FastOutSlowInEasing)) { shift } +
                        fadeIn(tween(90, easing = FastOutSlowInEasing)),
                    initialContentExit = fadeOut(tween(55)),
                    targetContentZIndex = 1f,
                    sizeTransform = SizeTransform(clip = true)
                )
            }
            else -> {
                ContentTransform(
                    targetContentEnter = fadeIn(tween(130)),
                    initialContentExit = fadeOut(tween(90)),
                    sizeTransform = SizeTransform(clip = true)
                )
            }
        }
    }
}

@Composable
fun MuWorkbenchTheme(
    style: ThemeStyle,
    accent: AccentStyle,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val mu = buildMuColors(style, accent, isDark)

    val m3 = if (isDark) {
        darkColorScheme(
            primary = mu.accent,
            onPrimary = Color.White,
            secondary = mu.accent2,
            background = mu.paper,
            onBackground = mu.ink,
            surface = mu.surface1,
            onSurface = mu.ink,
            surfaceVariant = mu.surface2,
            onSurfaceVariant = mu.ink2,
            surfaceContainer = mu.surface1,
            surfaceContainerLow = mu.surface1,
            surfaceContainerHigh = mu.surface2,
            surfaceContainerHighest = mu.surface3,
            surfaceContainerLowest = mu.paper,
            inverseSurface = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E),
            inverseOnSurface = if (isDark) Color(0xFF1C1C1E) else Color.White,
            outline = mu.line2,
            error = mu.danger
        )
    } else {
        lightColorScheme(
            primary = mu.accent,
            onPrimary = Color.White,
            secondary = mu.accent2,
            background = mu.paper,
            onBackground = mu.ink,
            surface = mu.surface1,
            onSurface = mu.ink,
            surfaceVariant = mu.surface2,
            onSurfaceVariant = mu.ink2,
            surfaceContainer = mu.surface1,
            surfaceContainerLow = mu.surface1,
            surfaceContainerHigh = mu.surface2,
            surfaceContainerHighest = mu.surface3,
            surfaceContainerLowest = mu.paper,
            inverseSurface = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E),
            inverseOnSurface = if (isDark) Color(0xFF1C1C1E) else Color.White,
            outline = mu.line2,
            error = mu.danger
        )
    }

    val shapes = Shapes(
        small = RoundedCornerShape(mu.cardRadius.coerceAtMost(8.dp)),
        medium = RoundedCornerShape(mu.cardRadius),
        large = RoundedCornerShape(if (style == ThemeStyle.IOS) 24.dp else mu.cardRadius)
    )

    CompositionLocalProvider(LocalMuColors provides mu) {
        MaterialTheme(
            colorScheme = m3,
            shapes = shapes,
            content = content
        )
    }
}
