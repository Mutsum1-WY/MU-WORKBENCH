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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mu.workbench.data.AccentStyle
import com.mu.workbench.data.ThemeStyle

enum class UiSkin { P5R, P3R, IOS }

@Immutable
data class MuColors(
    val style: ThemeStyle,
    val skin: UiSkin,
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
    val skin = when (style) {
        ThemeStyle.P5R -> UiSkin.P5R
        ThemeStyle.P3R -> UiSkin.P3R
        else -> UiSkin.IOS
    }
    val (acc, acc2, acc3) = when (accentStyle) {
        AccentStyle.BLUE -> when (style) {
            ThemeStyle.IOS -> Triple(Color(0xFF007AFF), Color(0xFF5AC8FA), Color(0xFF0056B3))
            ThemeStyle.P3R -> Triple(Color(0xFF015FCC), Color(0xFF16CFFB), Color(0xFF014CA3))
            ThemeStyle.P5R -> Triple(Color(0xFFE4002B), Color(0xFFFF3355), Color(0xFFB3001F))
            ThemeStyle.MINIMAL -> if (isDark) {
                Triple(Color(0xFF5A5A5E), Color(0xFF9A9AA0), Color(0xFF3A3A3C))
            } else {
                Triple(Color(0xFF2B2B2B), Color(0xFF6B6B6B), Color(0xFF111111))
            }
            ThemeStyle.GLASS -> Triple(Color(0xFF6C5CE7), Color(0xFF00C2FF), Color(0xFF4B3FC4))
            ThemeStyle.BENTO -> Triple(Color(0xFFFF6B35), Color(0xFFFFB627), Color(0xFFD9480F))
            ThemeStyle.BRUTAL -> Triple(Color(0xFFFF3D7F), Color(0xFFFFE600), Color(0xFFC2185B))
            ThemeStyle.CLAY -> Triple(Color(0xFF7C6CF2), Color(0xFFFF9EC7), Color(0xFF5A4BD1))
            ThemeStyle.OLED -> Triple(Color(0xFF0A84FF), Color(0xFF64D2FF), Color(0xFF0060DF))
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
                style = style, skin = skin, isDark = false,
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
                style = style, skin = skin, isDark = true,
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
                style = style, skin = skin, isDark = false,
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
                style = style, skin = skin, isDark = true,
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
                style = style, skin = skin, isDark = false,
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
                style = style, skin = skin, isDark = true,
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

        ThemeStyle.MINIMAL -> if (!isDark) {
            MuColors(
                style = style, skin = skin, isDark = false,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = Color(0xFF111111), accentSoft = acc.copy(alpha = 0.10f),
                paper = Color(0xFFFAFAFA), paper2 = Color(0xFFF2F2F2),
                surface1 = Color(0xFFFFFFFF), surface2 = Color(0xFFF7F7F7), surface3 = Color(0xFFEDEDED),
                ink = Color(0xFF111111), ink2 = Color(0xFF444444), ink3 = Color(0xFF8A8A8A),
                line = Color(0xFFE6E6E6), line2 = Color(0xFFD0D0D0),
                chrome = Color(0xFFFFFFFF), chrome2 = Color(0xFFFFFFFF), chromeInk = Color(0xFF111111), chromeSoft = Color(0xFF8A8A8A),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF1F8A4C), warn = Color(0xFFB7791F), danger = Color(0xFFC62828),
                hardShadowColor = Color.Transparent,
                cardRadius = 4.dp, cardBorderWidth = 1.dp, hardShadowDp = 0.dp
            )
        } else {
            MuColors(
                style = style, skin = skin, isDark = true,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = Color(0xFFE5E5E5), accentSoft = acc.copy(alpha = 0.18f),
                paper = Color(0xFF0B0B0B), paper2 = Color(0xFF141414),
                surface1 = Color(0xFF121212), surface2 = Color(0xFF1A1A1A), surface3 = Color(0xFF242424),
                ink = Color(0xFFF2F2F2), ink2 = Color(0xFFBDBDBD), ink3 = Color(0xFF7A7A7A),
                line = Color(0xFF262626), line2 = Color(0xFF3A3A3A),
                chrome = Color(0xFF0B0B0B), chrome2 = Color(0xFF0B0B0B), chromeInk = Color(0xFFF2F2F2), chromeSoft = Color(0xFF7A7A7A),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF4ADE80), warn = Color(0xFFFBBF24), danger = Color(0xFFF87171),
                hardShadowColor = Color.Transparent,
                cardRadius = 4.dp, cardBorderWidth = 1.dp, hardShadowDp = 0.dp
            )
        }

        ThemeStyle.GLASS -> if (!isDark) {
            MuColors(
                style = style, skin = skin, isDark = false,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = acc3, accentSoft = acc.copy(alpha = 0.14f),
                paper = Color(0xFFEEF0FF), paper2 = Color(0xFFE4E7FB),
                surface1 = Color(0xFFFFFFFF), surface2 = Color(0xFFF6F7FF), surface3 = Color(0xFFE9EBFA),
                ink = Color(0xFF1B1B3A), ink2 = Color(0xFF3D3F66), ink3 = Color(0xFF7A7C9E),
                line = Color(0x1F1B1B3A), line2 = Color(0x331B1B3A),
                chrome = Color(0xCCFFFFFF), chrome2 = Color(0xFFFFFFFF), chromeInk = Color(0xFF1B1B3A), chromeSoft = Color(0xFF7A7C9E),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF10B981), warn = Color(0xFFF59E0B), danger = Color(0xFFEF4444),
                hardShadowColor = Color.Transparent,
                cardRadius = 20.dp, cardBorderWidth = 1.dp, hardShadowDp = 0.dp
            )
        } else {
            MuColors(
                style = style, skin = skin, isDark = true,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = acc2, accentSoft = acc.copy(alpha = 0.24f),
                paper = Color(0xFF0B0B1E), paper2 = Color(0xFF14142B),
                surface1 = Color(0xFF1A1A33), surface2 = Color(0xFF222244), surface3 = Color(0xFF2C2C52),
                ink = Color(0xFFF4F4FF), ink2 = Color(0xFFC9CAEE), ink3 = Color(0xFF8C8DB5),
                line = Color(0x26FFFFFF), line2 = Color(0x40FFFFFF),
                chrome = Color(0xCC12122A), chrome2 = Color(0xFF1A1A33), chromeInk = Color(0xFFF4F4FF), chromeSoft = Color(0xFF8C8DB5),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF34D399), warn = Color(0xFFFBBF24), danger = Color(0xFFF87171),
                hardShadowColor = Color.Transparent,
                cardRadius = 20.dp, cardBorderWidth = 1.dp, hardShadowDp = 0.dp
            )
        }

        ThemeStyle.BENTO -> if (!isDark) {
            MuColors(
                style = style, skin = skin, isDark = false,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = acc3, accentSoft = acc.copy(alpha = 0.14f),
                paper = Color(0xFFF3F1EC), paper2 = Color(0xFFEAE7E0),
                surface1 = Color(0xFFFFFFFF), surface2 = Color(0xFFFAF8F4), surface3 = Color(0xFFEFECE5),
                ink = Color(0xFF1A1A1A), ink2 = Color(0xFF4A4A4A), ink3 = Color(0xFF8C8880),
                line = Color(0x14000000), line2 = Color(0x24000000),
                chrome = Color(0xFFF3F1EC), chrome2 = Color(0xFFFFFFFF), chromeInk = Color(0xFF1A1A1A), chromeSoft = Color(0xFF8C8880),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF2F9E44), warn = Color(0xFFF08C00), danger = Color(0xFFE03131),
                hardShadowColor = Color.Transparent,
                cardRadius = 22.dp, cardBorderWidth = 0.dp, hardShadowDp = 0.dp
            )
        } else {
            MuColors(
                style = style, skin = skin, isDark = true,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = acc2, accentSoft = acc.copy(alpha = 0.22f),
                paper = Color(0xFF111111), paper2 = Color(0xFF1A1A1A),
                surface1 = Color(0xFF1C1C1E), surface2 = Color(0xFF232325), surface3 = Color(0xFF2C2C2E),
                ink = Color(0xFFF5F5F5), ink2 = Color(0xFFC7C7C7), ink3 = Color(0xFF8E8E8E),
                line = Color(0x1AFFFFFF), line2 = Color(0x2EFFFFFF),
                chrome = Color(0xFF111111), chrome2 = Color(0xFF1C1C1E), chromeInk = Color(0xFFF5F5F5), chromeSoft = Color(0xFF8E8E8E),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF51CF66), warn = Color(0xFFFFA94D), danger = Color(0xFFFF6B6B),
                hardShadowColor = Color.Transparent,
                cardRadius = 22.dp, cardBorderWidth = 0.dp, hardShadowDp = 0.dp
            )
        }

        ThemeStyle.BRUTAL -> if (!isDark) {
            MuColors(
                style = style, skin = skin, isDark = false,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = acc3, accentSoft = acc.copy(alpha = 0.16f),
                paper = Color(0xFFFFF6E0), paper2 = Color(0xFFFFE9B8),
                surface1 = Color(0xFFFFFFFF), surface2 = Color(0xFFFFFBEF), surface3 = Color(0xFFFFE9B8),
                ink = Color(0xFF111111), ink2 = Color(0xFF2A2A2A), ink3 = Color(0xFF5C5C5C),
                line = Color(0xFF111111), line2 = Color(0x66111111),
                chrome = Color(0xFFFFE600), chrome2 = Color(0xFFFFE600), chromeInk = Color(0xFF111111), chromeSoft = Color(0xFF3A3A3A),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF00C46A), warn = Color(0xFFFF8A00), danger = Color(0xFFFF2E2E),
                hardShadowColor = Color(0xFF111111),
                cardRadius = 10.dp, cardBorderWidth = 2.5.dp, hardShadowDp = 4.dp
            )
        } else {
            MuColors(
                style = style, skin = skin, isDark = true,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = acc2, accentSoft = acc.copy(alpha = 0.22f),
                paper = Color(0xFF161616), paper2 = Color(0xFF222222),
                surface1 = Color(0xFF1F1F1F), surface2 = Color(0xFF262626), surface3 = Color(0xFF333333),
                ink = Color(0xFFFFF6E0), ink2 = Color(0xFFE6DCC4), ink3 = Color(0xFFA8A08C),
                line = Color(0xFFFFF6E0), line2 = Color(0x66FFF6E0),
                chrome = Color(0xFF111111), chrome2 = Color(0xFF111111), chromeInk = Color(0xFFFFE600), chromeSoft = Color(0xFFE6DCC4),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF22E584), warn = Color(0xFFFFA94D), danger = Color(0xFFFF5C5C),
                hardShadowColor = acc2,
                cardRadius = 10.dp, cardBorderWidth = 2.5.dp, hardShadowDp = 4.dp
            )
        }

        ThemeStyle.CLAY -> if (!isDark) {
            MuColors(
                style = style, skin = skin, isDark = false,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = acc3, accentSoft = acc.copy(alpha = 0.16f),
                paper = Color(0xFFEFEAFB), paper2 = Color(0xFFE6E0F7),
                surface1 = Color(0xFFF8F5FF), surface2 = Color(0xFFF2EEFC), surface3 = Color(0xFFE8E2F8),
                ink = Color(0xFF2D2A4A), ink2 = Color(0xFF55517A), ink3 = Color(0xFF8F8BB0),
                line = Color(0x142D2A4A), line2 = Color(0x242D2A4A),
                chrome = Color(0xFFEFEAFB), chrome2 = Color(0xFFF8F5FF), chromeInk = Color(0xFF2D2A4A), chromeSoft = Color(0xFF8F8BB0),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF34C38F), warn = Color(0xFFFFB547), danger = Color(0xFFFF6B8B),
                hardShadowColor = Color(0x405A4BD1),
                cardRadius = 26.dp, cardBorderWidth = 0.dp, hardShadowDp = 0.dp
            )
        } else {
            MuColors(
                style = style, skin = skin, isDark = true,
                accent = acc, accent2 = acc2, accent3 = acc3,
                accentInk = acc2, accentSoft = acc.copy(alpha = 0.24f),
                paper = Color(0xFF1E1B2E), paper2 = Color(0xFF25213A),
                surface1 = Color(0xFF2A2640), surface2 = Color(0xFF322D4C), surface3 = Color(0xFF3B3558),
                ink = Color(0xFFF3F0FF), ink2 = Color(0xFFCEC8EE), ink3 = Color(0xFF9893BD),
                line = Color(0x1FFFFFFF), line2 = Color(0x33FFFFFF),
                chrome = Color(0xFF1E1B2E), chrome2 = Color(0xFF2A2640), chromeInk = Color(0xFFF3F0FF), chromeSoft = Color(0xFF9893BD),
                p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
                ok = Color(0xFF4ADE9F), warn = Color(0xFFFFC46B), danger = Color(0xFFFF8FA8),
                hardShadowColor = Color(0x80000000),
                cardRadius = 26.dp, cardBorderWidth = 0.dp, hardShadowDp = 0.dp
            )
        }

        ThemeStyle.OLED -> MuColors(
            style = style, skin = skin, isDark = true,
            accent = acc, accent2 = acc2, accent3 = acc3,
            accentInk = acc2, accentSoft = acc.copy(alpha = 0.16f),
            paper = Color(0xFF000000), paper2 = Color(0xFF0A0A0A),
            surface1 = Color(0xFF0D0D0F), surface2 = Color(0xFF151518), surface3 = Color(0xFF1E1E22),
            ink = Color(0xFFEDEDED), ink2 = Color(0xFFA8A8AD), ink3 = Color(0xFF6B6B70),
            line = Color(0x14FFFFFF), line2 = Color(0x24FFFFFF),
            chrome = Color(0xFF000000), chrome2 = Color(0xFF000000), chromeInk = Color(0xFFEDEDED), chromeSoft = Color(0xFF6B6B70),
            p3Cyan = p3Cyan, p3Pink = p3Pink, p3Deep = p3Deep,
            ok = Color(0xFF30D158), warn = Color(0xFFFFD60A), danger = Color(0xFFFF453A),
            hardShadowColor = Color.Transparent,
            cardRadius = 16.dp, cardBorderWidth = 0.75.dp, hardShadowDp = 0.dp
        )
    }
}

object MuMotion {
    fun intOffsetSpring(skin: UiSkin): FiniteAnimationSpec<IntOffset> = when (skin) {
        UiSkin.IOS -> spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 620f
        )
        UiSkin.P3R -> tween(durationMillis = 190, easing = FastOutSlowInEasing)
        UiSkin.P5R -> tween(durationMillis = 160, easing = FastOutSlowInEasing)
    }

    fun floatSpring(skin: UiSkin): FiniteAnimationSpec<Float> = when (skin) {
        UiSkin.IOS -> spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 620f
        )
        UiSkin.P3R -> tween(durationMillis = 190, easing = FastOutSlowInEasing)
        UiSkin.P5R -> tween(durationMillis = 160, easing = FastOutSlowInEasing)
    }

    fun <T> tabTransition(
        skin: UiSkin,
        direction: Int
    ): AnimatedContentTransitionScope<T>.() -> ContentTransform = {
        val spec = intOffsetSpring(skin)
        when {
            skin == UiSkin.IOS && direction > 0 -> {
                ContentTransform(
                    targetContentEnter = slideInHorizontally(animationSpec = spec) { full -> full },
                    initialContentExit = slideOutHorizontally(animationSpec = spec) { full -> -(full * 0.25f).toInt() },
                    targetContentZIndex = 1f,
                    sizeTransform = SizeTransform(clip = true)
                )
            }
            skin == UiSkin.IOS && direction < 0 -> {
                ContentTransform(
                    targetContentEnter = slideInHorizontally(animationSpec = spec) { full -> -(full * 0.25f).toInt() },
                    initialContentExit = slideOutHorizontally(animationSpec = spec) { full -> full },
                    targetContentZIndex = -1f,
                    sizeTransform = SizeTransform(clip = true)
                )
            }
            skin == UiSkin.P5R -> {
                val shift = if (direction > 0) 80 else if (direction < 0) -80 else 0
                ContentTransform(
                    targetContentEnter = slideInHorizontally(tween(150, easing = FastOutSlowInEasing)) { shift } +
                        fadeIn(tween(80, easing = FastOutSlowInEasing)),
                    initialContentExit = fadeOut(tween(50)),
                    targetContentZIndex = 1f,
                    sizeTransform = SizeTransform(clip = true)
                )
            }
            skin == UiSkin.P3R -> {
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
    val isDark = style == ThemeStyle.OLED || isSystemInDarkTheme()
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
            inverseSurface = Color(0xFFF2F2F7),
            inverseOnSurface = Color(0xFF1C1C1E),
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
            inverseSurface = Color(0xFF1C1C1E),
            inverseOnSurface = Color.White,
            outline = mu.line2,
            error = mu.danger
        )
    }

    val shapes = Shapes(
        small = RoundedCornerShape(mu.cardRadius.coerceAtMost(8.dp)),
        medium = RoundedCornerShape(mu.cardRadius),
        large = RoundedCornerShape(
            when (style) {
                ThemeStyle.IOS -> 24.dp
                ThemeStyle.GLASS -> 24.dp
                ThemeStyle.BENTO -> 26.dp
                ThemeStyle.CLAY -> 28.dp
                else -> mu.cardRadius
            }
        )
    )

    val themeIndication = remember(mu) { MuThemeIndication(mu) }

    CompositionLocalProvider(LocalMuColors provides mu) {
        MaterialTheme(
            colorScheme = m3,
            shapes = shapes
        ) {
            CompositionLocalProvider(LocalIndication provides themeIndication) {
                content()
            }
        }
    }
}
