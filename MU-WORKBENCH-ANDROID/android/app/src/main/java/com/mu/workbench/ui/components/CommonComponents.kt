package com.mu.workbench.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mu.workbench.data.ThemeStyle
import com.mu.workbench.data.TodoItem
import com.mu.workbench.ui.theme.LocalMuColors
import com.mu.workbench.ui.theme.UiSkin
import com.mu.workbench.ui.theme.MuMotion
import com.mu.workbench.ui.theme.muPressTransform
import com.mu.workbench.ui.theme.muTactileClickable
import java.util.concurrent.ConcurrentHashMap

/**
 * 单子节点零卡顿切屏过渡：
 * 同时只保留当前目标页 1 份组合树，纯 translationX 硬件位移（alpha=1f 避免全屏 saveLayerAlpha 离屏开销），
 * 快速连点切页时自动取消前动画并直接渲染最新页面，彻底杜绝多页并发合成掉帧与重影。
 */
@Composable
fun <T> MuTabTransition(
    targetState: T,
    direction: Int,
    style: UiSkin,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    val offsetFrac = remember { Animatable(0f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(targetState) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        val shift = if (style == UiSkin.IOS) 0.14f else 0.08f
        val startOffset = when {
            direction > 0 -> shift
            direction < 0 -> -shift
            else -> 0f
        }
        offsetFrac.snapTo(startOffset)
        offsetFrac.animateTo(0f, MuMotion.floatSpring(style))
    }

    Box(
        modifier = modifier.graphicsLayer {
            translationX = offsetFrac.value * size.width
            clip = true
        }
    ) {
        key(targetState) {
            content(targetState)
        }
    }
}

private val parallelogramShapeCache = ConcurrentHashMap<Int, Shape>()
private val beveledShapeCache = ConcurrentHashMap<Int, Shape>()

/** 斜切平行四边形裁切形状（全局缓存实例，避免每次重组失效轮廓并触发重绘） */
fun parallelogramShape(skewPxRatio: Float = 0.14f): Shape {
    val key = (skewPxRatio * 1000f).toInt()
    return parallelogramShapeCache.computeIfAbsent(key) {
        GenericShape { size, _ ->
            val dx = (size.height * skewPxRatio).coerceAtMost(size.width * 0.25f)
            moveTo(dx, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width - dx, size.height)
            lineTo(0f, size.height)
            close()
        }
    }
}

/** 切角方块形状（右上角切斜，全局缓存实例） */
fun beveledCornerShape(bevelDpRatio: Float = 0.25f): Shape {
    val key = (bevelDpRatio * 1000f).toInt()
    return beveledShapeCache.computeIfAbsent(key) {
        GenericShape { size, _ ->
            val b = size.minDimension * bevelDpRatio
            moveTo(0f, 0f)
            lineTo(size.width - b, 0f)
            lineTo(size.width, b)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
    }
}

/**
 * 全屏主题背景纹样（P5R/P3R 均使用 drawWithCache + ImageShader  tile 平铺缓存，零每帧路径/循环开销）
 */
@Composable
fun MuPageBackground(
    chapterNumber: String = "01",
    modifier: Modifier = Modifier
) {
    val mu = LocalMuColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(mu.paper)
    ) {
        if (mu.skin == UiSkin.P5R) {
            val dotColor = if (mu.isDark) Color.White.copy(alpha = 0.05f) else Color(0xFF0B0B0C).copy(alpha = 0.085f)
            val slashTint = mu.accent.copy(alpha = if (mu.isDark) 0.10f else 0.065f)
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val slash = Path().apply {
                            moveTo(size.width * 0.68f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, size.height)
                            lineTo(size.width * 0.42f, size.height)
                            close()
                        }
                        val tilePx = 20.dp.toPx().toInt().coerceAtLeast(12)
                        val tile = ImageBitmap(tilePx, tilePx)
                        val tileCanvas = androidx.compose.ui.graphics.Canvas(tile)
                        val dotPaint = Paint().apply {
                            color = dotColor
                            isAntiAlias = true
                        }
                        val r = 1.1.dp.toPx()
                        val q = tilePx / 4f
                        tileCanvas.drawCircle(Offset(q, q), r, dotPaint)
                        tileCanvas.drawCircle(Offset(tilePx * 0.75f, tilePx * 0.75f), r, dotPaint)
                        val shaderPaint = Paint().apply {
                            shader = ImageShader(tile, TileMode.Repeated, TileMode.Repeated)
                        }
                        val fullRect = Rect(0f, 0f, size.width, size.height)
                        onDrawBehind {
                            drawPath(slash, slashTint)
                            drawContext.canvas.drawRect(fullRect, shaderPaint)
                        }
                    }
            )
        } else if (mu.skin == UiSkin.P3R) {
            val lineColor = if (mu.isDark) mu.p3Cyan.copy(alpha = 0.05f) else Color(0xFF031F64).copy(alpha = 0.035f)
            val glowTop = mu.p3Cyan.copy(alpha = if (mu.isDark) 0.16f else 0.18f)
            val glowSide = mu.accent.copy(alpha = if (mu.isDark) 0.25f else 0.10f)
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val b1 = Brush.radialGradient(
                            colors = listOf(glowTop, Color.Transparent),
                            center = Offset(size.width * 0.92f, 0f),
                            radius = size.width * 0.85f
                        )
                        val b2 = Brush.radialGradient(
                            colors = listOf(glowSide, Color.Transparent),
                            center = Offset(0f, size.height * 0.15f),
                            radius = size.width * 0.7f
                        )
                        // 115° 无缝斜线贴图瓦片（单次 Shader Blit 替代每帧描边 80 条整屏斜线）
                        val tileW = 16.dp.toPx().toInt().coerceAtLeast(16)
                        val tileH = (tileW / 0.48f).toInt().coerceAtLeast(32)
                        val tile = ImageBitmap(tileW, tileH)
                        val tileCanvas = androidx.compose.ui.graphics.Canvas(tile)
                        val linePaint = Paint().apply {
                            color = lineColor
                            strokeWidth = 1.dp.toPx()
                            style = PaintingStyle.Stroke
                            isAntiAlias = true
                        }
                        val fw = tileW.toFloat()
                        val fh = tileH.toFloat()
                        tileCanvas.drawLine(Offset(-fw, 0f), Offset(0f, fh), linePaint)
                        tileCanvas.drawLine(Offset(0f, 0f), Offset(fw, fh), linePaint)
                        tileCanvas.drawLine(Offset(fw, 0f), Offset(fw * 2f, fh), linePaint)
                        val lineShaderPaint = Paint().apply {
                            shader = ImageShader(tile, TileMode.Repeated, TileMode.Repeated)
                        }
                        val fullRect = Rect(0f, 0f, size.width, size.height)
                        onDrawBehind {
                            drawCircle(brush = b1, center = Offset(size.width * 0.92f, 0f), radius = size.width * 0.85f)
                            drawCircle(brush = b2, center = Offset(0f, size.height * 0.15f), radius = size.width * 0.7f)
                            drawContext.canvas.drawRect(fullRect, lineShaderPaint)
                        }
                    }
            )
            // P3R 右下角巨型半透明斜体章节水印（仅纯数字用斜体）
            Text(
                text = chapterNumber,
                color = mu.ink.copy(alpha = if (mu.isDark) 0.06f else 0.05f),
                fontSize = 148.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-8).sp,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 78.dp)
            )
        } else {
            when (mu.style) {
                ThemeStyle.GLASS -> {
                    val a1 = if (mu.isDark) 0.35f else 0.55f
                    val a2 = if (mu.isDark) 0.28f else 0.45f
                    val a3 = if (mu.isDark) 0.25f else 0.40f
                    val c1 = mu.accent.copy(alpha = a1)
                    val c2 = mu.accent2.copy(alpha = a2)
                    val c3 = Color(0xFFFF7AC6).copy(alpha = a3)
                    Spacer(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val pt1 = Offset(size.width * 0.10f, size.height * 0.05f)
                                val r1 = (size.width * 0.9f).coerceAtLeast(32f)
                                val pt2 = Offset(size.width * 0.95f, size.height * 0.35f)
                                val r2 = (size.width * 0.8f).coerceAtLeast(32f)
                                val pt3 = Offset(size.width * 0.20f, size.height * 0.85f)
                                val r3 = (size.width * 0.9f).coerceAtLeast(32f)
                                val b1 = Brush.radialGradient(listOf(c1, Color.Transparent), center = pt1, radius = r1)
                                val b2 = Brush.radialGradient(listOf(c2, Color.Transparent), center = pt2, radius = r2)
                                val b3 = Brush.radialGradient(listOf(c3, Color.Transparent), center = pt3, radius = r3)
                                onDrawBehind {
                                    drawCircle(b1, radius = r1, center = pt1)
                                    drawCircle(b2, radius = r2, center = pt2)
                                    drawCircle(b3, radius = r3, center = pt3)
                                }
                            }
                    )
                }
                ThemeStyle.MINIMAL -> {
                    val gridCol = mu.ink.copy(alpha = 0.035f)
                    Spacer(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val tilePx = 24.dp.toPx().toInt().coerceAtLeast(16)
                                val tile = ImageBitmap(tilePx, tilePx)
                                val tileCanvas = androidx.compose.ui.graphics.Canvas(tile)
                                val lp = Paint().apply {
                                    color = gridCol
                                    strokeWidth = 1f
                                    style = PaintingStyle.Stroke
                                }
                                val f = tilePx.toFloat()
                                tileCanvas.drawLine(Offset(0f, 0f), Offset(f, 0f), lp)
                                tileCanvas.drawLine(Offset(0f, 0f), Offset(0f, f), lp)
                                val shaderPaint = Paint().apply {
                                    shader = ImageShader(tile, TileMode.Repeated, TileMode.Repeated)
                                }
                                val fullRect = Rect(0f, 0f, size.width, size.height)
                                onDrawBehind {
                                    drawContext.canvas.drawRect(fullRect, shaderPaint)
                                }
                            }
                    )
                }
                ThemeStyle.BRUTAL -> {
                    val dotCol = mu.ink.copy(alpha = 0.07f)
                    Spacer(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val tilePx = 20.dp.toPx().toInt().coerceAtLeast(12)
                                val tile = ImageBitmap(tilePx, tilePx)
                                val tileCanvas = androidx.compose.ui.graphics.Canvas(tile)
                                val dotPaint = Paint().apply {
                                    color = dotCol
                                    isAntiAlias = true
                                }
                                val r = 1.1.dp.toPx()
                                val q = tilePx / 4f
                                tileCanvas.drawCircle(Offset(q, q), r, dotPaint)
                                tileCanvas.drawCircle(Offset(tilePx * 0.75f, tilePx * 0.75f), r, dotPaint)
                                val shaderPaint = Paint().apply {
                                    shader = ImageShader(tile, TileMode.Repeated, TileMode.Repeated)
                                }
                                val fullRect = Rect(0f, 0f, size.width, size.height)
                                onDrawBehind {
                                    drawContext.canvas.drawRect(fullRect, shaderPaint)
                                }
                            }
                    )
                }
                ThemeStyle.CLAY -> {
                    val b1Col = mu.accent2.copy(alpha = 0.18f)
                    val b2Col = mu.accent.copy(alpha = 0.12f)
                    Spacer(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val pt1 = Offset(size.width * 0.88f, size.height * 0.10f)
                                val pt2 = Offset(size.width * 0.12f, size.height * 0.85f)
                                val r = (size.width * 0.75f).coerceAtLeast(32f)
                                val br1 = Brush.radialGradient(listOf(b1Col, Color.Transparent), center = pt1, radius = r)
                                val br2 = Brush.radialGradient(listOf(b2Col, Color.Transparent), center = pt2, radius = r)
                                onDrawBehind {
                                    drawCircle(br1, radius = r, center = pt1)
                                    drawCircle(br2, radius = r, center = pt2)
                                }
                            }
                    )
                }
                ThemeStyle.OLED -> {
                    val glow = mu.accent.copy(alpha = 0.08f)
                    Spacer(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithCache {
                                val pt = Offset(size.width * 0.5f, 0f)
                                val r = (size.width * 0.9f).coerceAtLeast(32f)
                                val br = Brush.radialGradient(listOf(glow, Color.Transparent), center = pt, radius = r)
                                onDrawBehind {
                                    drawCircle(br, radius = r, center = pt)
                                }
                            }
                    )
                }
                else -> Unit
            }
        }
    }
}

/**
 * 零残影零分配主题卡片：
 * 阴影、边框与角标 Path/Brush 全部在 drawWithCache 中按尺寸缓存，滑动时零对象分配。
 */
@Composable
fun MuCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    padding: Dp = 11.dp,
    showCornerAccent: Boolean = true,
    behindContent: (DrawScope.() -> Unit)? = null,
    backdropModifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val mu = LocalMuColors.current
    val haptic = LocalHapticFeedback.current
    val shape: Shape = RoundedCornerShape(mu.cardRadius)
    val solidBg = if (mu.style == ThemeStyle.GLASS) {
        when {
            backgroundColor == null -> Color.White.copy(alpha = if (mu.isDark) 0.07f else 0.55f)
            backgroundColor.alpha >= 0.99f -> backgroundColor.copy(alpha = if (mu.isDark) 0.55f else 0.70f)
            else -> backgroundColor
        }
    } else {
        val bg = backgroundColor ?: mu.surface1
        if (bg.alpha < 1f) bg.compositeOver(mu.surface1) else bg
    }
    val bd = borderColor ?: mu.line
    val shadowCol = if (borderColor != null && mu.skin == UiSkin.P5R) borderColor else mu.hardShadowColor
    val src = if (onClick != null) remember { MutableInteractionSource() } else null
    val pressed by (src?.collectIsPressedAsState() ?: remember { mutableStateOf(false) })
    val hardShadowAnim by animateDpAsState(
        targetValue = if (pressed) mu.hardShadowDp * 0.4f else mu.hardShadowDp,
        animationSpec = spring(stiffness = 900f),
        label = "cardHardShadow"
    )
    val rootMod = modifier
        .fillMaxWidth()
        .let { m -> if (src != null) m.muPressTransform(src) else m }

    val base = if ((mu.skin == UiSkin.P5R || mu.style == ThemeStyle.BRUTAL) && mu.hardShadowDp > 0.dp) {
        val isRound = mu.style == ThemeStyle.BRUTAL
        rootMod
            .padding(end = mu.hardShadowDp, bottom = mu.hardShadowDp)
            .drawWithCache {
                val r = mu.cardRadius.toPx()
                val cr = CornerRadius(r, r)
                onDrawBehind {
                    val sh = hardShadowAnim.toPx()
                    val tl = Offset(sh, sh)
                    if (isRound) {
                        drawRoundRect(color = shadowCol, topLeft = tl, size = size, cornerRadius = cr)
                    } else {
                        drawRect(color = shadowCol, topLeft = tl, size = size)
                    }
                }
            }
    } else if (mu.skin == UiSkin.P3R) {
        rootMod
            .padding(end = 2.dp, bottom = 3.dp)
            .drawWithCache {
                val r = mu.cardRadius.toPx()
                val cr = CornerRadius(r, r)
                val tl = Offset(1.dp.toPx(), 3.dp.toPx())
                val p3ShadowCol = mu.hardShadowColor
                onDrawBehind {
                    drawRoundRect(color = p3ShadowCol, topLeft = tl, size = size, cornerRadius = cr)
                }
            }
    } else {
        when (mu.style) {
            ThemeStyle.MINIMAL, ThemeStyle.OLED, ThemeStyle.GLASS -> rootMod
            ThemeStyle.BENTO -> rootMod.shadow(1.dp, shape, ambientColor = Color.Black.copy(alpha = 0.05f))
            ThemeStyle.CLAY -> rootMod.shadow(10.dp, shape, ambientColor = mu.hardShadowColor, spotColor = mu.hardShadowColor)
            else -> rootMod.shadow(2.dp, shape, ambientColor = Color.Black.copy(alpha = 0.06f))
        }
    }

    val effectiveBorderW = if (borderColor != null) maxOf(mu.cardBorderWidth, 1.5.dp) else mu.cardBorderWidth
    val borderMod = when {
        mu.style == ThemeStyle.GLASS && borderColor == null -> Modifier.border(
            BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = if (mu.isDark) 0.28f else 0.90f),
                        Color.White.copy(alpha = if (mu.isDark) 0.06f else 0.35f)
                    )
                )
            ),
            shape
        )
        effectiveBorderW > 0.dp -> Modifier.border(effectiveBorderW, bd, shape)
        else -> Modifier
    }

    val finalMod = base
        .clip(shape)
        .background(solidBg)
        .then(backdropModifier)
        .then(if (behindContent != null) Modifier.drawBehind { behindContent() } else Modifier)
        .then(borderMod)
        .let { m ->
            if (mu.style == ThemeStyle.CLAY) {
                val topAlpha = if (mu.isDark) 0.10f else 0.55f
                val botShadow = mu.hardShadowColor.copy(alpha = 0.50f)
                m.drawWithCache {
                    val h = size.height.coerceAtLeast(2f)
                    val topBrush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = topAlpha), Color.Transparent),
                        startY = 0f,
                        endY = h * 0.45f
                    )
                    val botBrush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, botShadow),
                        startY = h * 0.70f,
                        endY = h
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = topBrush, size = Size(size.width, h * 0.45f))
                        drawRect(brush = botBrush, topLeft = Offset(0f, h * 0.70f), size = Size(size.width, h * 0.30f))
                    }
                }
            } else m
        }
        .let { m ->
            if (!showCornerAccent || mu.skin == UiSkin.IOS) m
            else m.drawWithCache {
                if (mu.skin == UiSkin.P5R) {
                    val tri = 16.dp.toPx()
                    val p = Path().apply {
                        moveTo(size.width - tri, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, tri)
                        close()
                    }
                    val acc = mu.accent
                    onDrawWithContent {
                        drawContent()
                        drawPath(p, acc)
                    }
                } else {
                    val w = 28.dp.toPx()
                    val h = 5.dp.toPx()
                    val rightPad = 10.dp.toPx()
                    val topPad = 9.dp.toPx()
                    val skew = 4.dp.toPx()
                    val p = Path().apply {
                        moveTo(size.width - rightPad - w, topPad)
                        lineTo(size.width - rightPad, topPad)
                        lineTo(size.width - rightPad - skew, topPad + h)
                        lineTo(size.width - rightPad - w - skew, topPad + h)
                        close()
                    }
                    val br = Brush.horizontalGradient(listOf(mu.accent, mu.p3Cyan))
                    onDrawWithContent {
                        drawContent()
                        drawPath(p, br)
                    }
                }
            }
        }
        .then(
            if (onClick != null && src != null) {
                Modifier.clickable(
                    interactionSource = src,
                    indication = LocalIndication.current
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            } else Modifier
        )
        .padding(padding)

    Column(modifier = finalMod, content = content)
}

@Composable
fun MuSectionHeader(
    title: String,
    badge: String? = null,
    modifier: Modifier = Modifier
) {
    val mu = LocalMuColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (mu.style == ThemeStyle.MINIMAL) {
            Text(
                text = title,
                color = mu.ink,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                fontSize = 13.sp
            )
            Spacer(Modifier.width(8.dp))
            HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = mu.line)
            if (!badge.isNullOrEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = badge,
                    color = mu.ink3,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (mu.style == ThemeStyle.BRUTAL) {
            val tagShape = RoundedCornerShape(6.dp)
            Box(
                modifier = Modifier
                    .padding(end = 3.dp, bottom = 3.dp)
                    .drawBehind {
                        val s = 3.dp.toPx()
                        val cr = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        drawRoundRect(mu.ink, topLeft = Offset(s, s), size = size, cornerRadius = cr)
                    }
                    .clip(tagShape)
                    .background(mu.accent2)
                    .border(2.dp, mu.ink, tagShape)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = title,
                    color = mu.ink,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.weight(1f))
            if (!badge.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(tagShape)
                        .background(mu.ink)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badge,
                        color = mu.accent2,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        } else when (mu.skin) {
            UiSkin.P5R -> {
                Box(
                    modifier = Modifier
                        .size(width = 6.dp, height = 17.dp)
                        .graphicsLayer { rotationZ = 10f }
                        .background(mu.accent)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    color = mu.ink,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Normal,
                    letterSpacing = (-0.2).sp,
                    fontSize = 15.5.sp,
                    modifier = Modifier.weight(1f)
                )
                if (!badge.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(parallelogramShape(0.2f))
                            .background(mu.chrome)
                            .border(1.5.dp, mu.ink, parallelogramShape(0.2f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badge,
                            color = mu.chromeInk,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            UiSkin.P3R -> {
                Canvas(modifier = Modifier.size(12.dp)) {
                    val p = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(p, mu.accent)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    color = mu.ink,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.5.sp
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.5.dp)
                        .graphicsLayer { rotationZ = -25f }
                        .background(mu.p3Cyan)
                )
                Spacer(Modifier.weight(1f))
                if (!badge.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(parallelogramShape(0.2f))
                            .background(if (mu.isDark) mu.surface2 else mu.p3Deep)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badge,
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            UiSkin.IOS -> {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(16.dp)
                        .background(mu.accent, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    color = mu.ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                if (!badge.isNullOrEmpty()) {
                    Surface(
                        color = mu.surface2,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, mu.line2)
                    ) {
                        Text(
                            text = badge,
                            color = mu.ink2,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun <T> MuSegmentedRow(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val mu = LocalMuColors.current
    val shape = when (mu.style) {
        ThemeStyle.BRUTAL -> RoundedCornerShape(10.dp)
        ThemeStyle.MINIMAL -> RoundedCornerShape(0.dp)
        ThemeStyle.CLAY -> RoundedCornerShape(999.dp)
        ThemeStyle.GLASS -> RoundedCornerShape(14.dp)
        else -> when (mu.skin) {
            UiSkin.IOS -> RoundedCornerShape(12.dp)
            UiSkin.P3R -> RoundedCornerShape(6.dp)
            UiSkin.P5R -> RoundedCornerShape(0.dp)
        }
    }
    val containerMod = when (mu.style) {
        ThemeStyle.BRUTAL -> Modifier
            .clip(shape)
            .background(mu.surface1)
            .border(2.dp, mu.ink, shape)
        ThemeStyle.MINIMAL -> Modifier
            .drawBehind {
                val h = 1.dp.toPx()
                drawRect(mu.line, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
            }
        ThemeStyle.GLASS -> Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = if (mu.isDark) 0.10f else 0.35f))
            .border(1.dp, Color.White.copy(alpha = if (mu.isDark) 0.22f else 0.60f), shape)
        ThemeStyle.OLED -> Modifier.clip(shape).background(mu.surface2)
        else -> {
            val containerBg = when (mu.skin) {
                UiSkin.P5R -> mu.chrome
                UiSkin.P3R -> mu.p3Deep
                UiSkin.IOS -> mu.surface3
            }
            Modifier.clip(shape).background(containerBg)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(containerMod)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items.forEach { (key, label) ->
            val isOn = key == selected
            val bg by animateColorAsState(
                targetValue = when {
                    mu.style == ThemeStyle.MINIMAL -> Color.Transparent
                    isOn && mu.style == ThemeStyle.BRUTAL -> mu.accent2
                    isOn && mu.style == ThemeStyle.GLASS -> Color.White.copy(alpha = if (mu.isDark) 0.24f else 0.85f)
                    isOn && mu.style == ThemeStyle.OLED -> mu.accent.copy(alpha = 0.22f)
                    isOn && mu.skin == UiSkin.P5R -> mu.accent
                    isOn && mu.skin == UiSkin.P3R -> Color.White
                    isOn -> mu.surface1
                    else -> Color.Transparent
                },
                animationSpec = tween(if (mu.skin == UiSkin.IOS) 240 else 140),
                label = "segBg"
            )
            val txtColor = when {
                isOn && mu.style == ThemeStyle.BRUTAL -> mu.ink
                isOn && mu.style == ThemeStyle.OLED -> mu.accent2
                isOn && mu.skin == UiSkin.P5R -> Color.White
                isOn && mu.skin == UiSkin.P3R -> mu.p3Deep
                isOn -> mu.ink
                mu.skin == UiSkin.P5R -> mu.chromeSoft
                mu.skin == UiSkin.P3R -> Color(0xFFB8EAFF)
                else -> mu.ink2
            }
            val itemShape = when (mu.style) {
                ThemeStyle.BRUTAL -> RoundedCornerShape(7.dp)
                ThemeStyle.MINIMAL -> RoundedCornerShape(2.dp)
                ThemeStyle.CLAY -> RoundedCornerShape(999.dp)
                ThemeStyle.GLASS -> RoundedCornerShape(11.dp)
                else -> when (mu.skin) {
                    UiSkin.IOS -> RoundedCornerShape(9.dp)
                    UiSkin.P3R -> if (isOn) parallelogramShape(0.18f) else RoundedCornerShape(4.dp)
                    UiSkin.P5R -> RoundedCornerShape(0.dp)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isOn && mu.style == ThemeStyle.CLAY) {
                            Modifier.shadow(4.dp, itemShape, spotColor = mu.hardShadowColor)
                        } else Modifier
                    )
                    .clip(itemShape)
                    .background(bg)
                    .then(
                        when {
                            isOn && mu.style == ThemeStyle.BRUTAL -> Modifier.border(2.dp, mu.ink, itemShape)
                            isOn && mu.style == ThemeStyle.MINIMAL -> Modifier.drawBehind {
                                val h = 2.dp.toPx()
                                drawRect(mu.ink, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                            }
                            else -> Modifier
                        }
                    )
                    .muTactileClickable { onSelect(key) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = txtColor,
                    fontSize = 13.sp,
                    fontWeight = if (isOn) {
                        if (mu.style == ThemeStyle.BRUTAL) FontWeight.Black else FontWeight.ExtraBold
                    } else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MuChip(
    text: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val mu = LocalMuColors.current
    val shape = when (mu.skin) {
        UiSkin.P5R -> RoundedCornerShape(0.dp)
        UiSkin.P3R -> parallelogramShape(0.18f)
        UiSkin.IOS -> RoundedCornerShape(999.dp)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .padding(horizontal = if (mu.skin == UiSkin.P3R) 9.dp else 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoCardRow(
    todo: TodoItem,
    isDone: Boolean,
    overdueDays: Long,
    index: Int = 0,
    onToggleDone: () -> Unit,
    onToggleSubStep: (String) -> Unit,
    onEdit: () -> Unit,
    onPostpone: () -> Unit,
    onDelete: () -> Unit
) {
    val mu = LocalMuColors.current
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleDone()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val bg = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> mu.ok
                SwipeToDismissBoxValue.EndToStart -> mu.danger
                else -> Color.Transparent
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(mu.cardRadius))
                    .background(bg)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (dir == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isDone) "撤销完成" else "完成", color = Color.White, fontWeight = FontWeight.Bold)
                } else if (dir == SwipeToDismissBoxValue.EndToStart) {
                    Text("左滑删除", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color.White)
                }
            }
        }
    ) {
        val showIndexStrip = mu.skin != UiSkin.IOS
        val stripColor = when {
            overdueDays > 0 && !isDone -> mu.danger
            isDone -> mu.ink3
            mu.skin == UiSkin.P3R -> mu.accent
            else -> mu.ink
        }

        MuCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = if (isDone) mu.paper2 else mu.surface1,
            borderColor = if (overdueDays > 0 && !isDone) mu.danger else null,
            padding = 0.dp,
            showCornerAccent = false,
            onClick = onEdit
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val stripW = 24.dp.toPx()
                        onDrawBehind {
                            if (showIndexStrip) {
                                drawRect(
                                    color = stripColor,
                                    topLeft = Offset.Zero,
                                    size = Size(stripW, size.height)
                                )
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showIndexStrip) {
                    Box(
                        modifier = Modifier.width(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "%02d".format(index + 1),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            modifier = Modifier.graphicsLayer { rotationZ = -90f },
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    val chkShape = when (mu.style) {
                        ThemeStyle.MINIMAL -> RoundedCornerShape(3.dp)
                        ThemeStyle.BRUTAL -> RoundedCornerShape(6.dp)
                        else -> when (mu.skin) {
                            UiSkin.IOS -> CircleShape
                            UiSkin.P3R -> beveledCornerShape(0.24f)
                            UiSkin.P5R -> RoundedCornerShape(0.dp)
                        }
                    }
                    val chkBorderW = when {
                        mu.style == ThemeStyle.MINIMAL -> 1.dp
                        mu.style == ThemeStyle.BRUTAL -> 2.5.dp
                        mu.skin == UiSkin.P5R -> 2.dp
                        else -> 1.5.dp
                    }
                    val chkBorderCol = when {
                        isDone -> mu.accent
                        mu.style == ThemeStyle.BRUTAL || mu.skin == UiSkin.P5R -> mu.ink
                        else -> mu.ink3
                    }
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(22.dp)
                            .clip(chkShape)
                            .background(if (isDone) mu.accent else Color.Transparent)
                            .border(chkBorderW, chkBorderCol, chkShape)
                            .muTactileClickable(onClick = onToggleDone),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                    }

                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = todo.title,
                            color = if (isDone) mu.ink3 else mu.ink,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (overdueDays > 0 && !isDone) {
                                MuChip("逾期 ${overdueDays} 天", mu.danger, Color.White)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .graphicsLayer { rotationZ = 45f }
                                        .background(Color(todo.priority.colorHex))
                                )
                                Text(
                                    text = todo.priority.label,
                                    color = mu.ink2,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Normal
                                )
                            }
                            MuChip(todo.tag, mu.accentSoft, mu.accentInk)
                            if (todo.remindTime.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = mu.ink3, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text(todo.remindTime, color = mu.ink3, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (todo.type.key != "once") {
                                Text(todo.type.label, color = mu.ink3, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (todo.subSteps.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                todo.subSteps.forEach { st ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { onToggleSubStep(st.id) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(RoundedCornerShape(if (mu.skin == UiSkin.P5R) 0.dp else 3.dp))
                                                .background(if (st.done) mu.accent else Color.Transparent)
                                                .border(1.2.dp, if (st.done) mu.accent else mu.ink3, RoundedCornerShape(if (mu.skin == UiSkin.P5R) 0.dp else 3.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (st.done) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            st.text,
                                            fontSize = 12.sp,
                                            color = if (st.done) mu.ink3 else mu.ink2,
                                            textDecoration = if (st.done) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!isDone) {
                        val postponeSrc = remember { MutableInteractionSource() }
                        TextButton(
                            onClick = onPostpone,
                            interactionSource = postponeSrc,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .muPressTransform(postponeSrc)
                        ) {
                            Text("明天", fontSize = 11.sp, color = mu.accentInk, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
