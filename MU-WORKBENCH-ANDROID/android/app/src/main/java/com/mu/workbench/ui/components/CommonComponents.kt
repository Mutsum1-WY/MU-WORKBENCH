package com.mu.workbench.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.mu.workbench.ui.theme.MuMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 单子节点零卡顿切屏过渡：
 * 同时只保留当前目标页 1 份组合树，用 Animatable + graphicsLayer 实现丝滑平移/渐入，
 * 快速连点切页时自动取消前动画并直接渲染最新页面，彻底杜绝多页并发合成卡屏与重影。
 */
@Composable
fun <T> MuTabTransition(
    targetState: T,
    direction: Int,
    style: ThemeStyle,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    val offsetFrac = remember { Animatable(0f) }
    val alphaAnim = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(targetState) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        val shift = if (style == ThemeStyle.IOS) 0.14f else 0.08f
        val startOffset = when {
            direction > 0 -> shift
            direction < 0 -> -shift
            else -> 0f
        }
        offsetFrac.snapTo(startOffset)
        alphaAnim.snapTo(if (style == ThemeStyle.IOS) 0.92f else 0.86f)
        coroutineScope {
            launch { offsetFrac.animateTo(0f, MuMotion.floatSpring(style)) }
            launch { alphaAnim.animateTo(1f, tween(if (style == ThemeStyle.IOS) 150 else 110)) }
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            translationX = offsetFrac.value * size.width
            alpha = alphaAnim.value
            clip = true
        }
    ) {
        key(targetState) {
            content(targetState)
        }
    }
}

/** 斜切平行四边形裁切形状，保留原版 P5R/P3R 标志性斜切视觉 */
fun parallelogramShape(skewPxRatio: Float = 0.14f): Shape = GenericShape { size, _ ->
    val dx = (size.height * skewPxRatio).coerceAtMost(size.width * 0.25f)
    moveTo(dx, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - dx, size.height)
    lineTo(0f, size.height)
    close()
}

/** 切角方块形状（右上角切斜）用于 P3R 复选框和色块 */
fun beveledCornerShape(bevelDpRatio: Float = 0.25f): Shape = GenericShape { size, _ ->
    val b = size.minDimension * bevelDpRatio
    moveTo(0f, 0f)
    lineTo(size.width - b, 0f)
    lineTo(size.width, b)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

/**
 * 全屏主题背景纹样（使用 drawWithCache + ImageShader 平铺缓存，零循环开销、零重影）
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
        if (mu.style == ThemeStyle.P5R) {
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
                        // 把交错半调网点预先绘入 1 个 tile 位图，GPU 重复平铺（替代每帧 3000 次 drawCircle）
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
        } else if (mu.style == ThemeStyle.P3R) {
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
                        val linesPath = Path().apply {
                            val gap = 16.dp.toPx()
                            var x = -size.height
                            while (x < size.width + size.height) {
                                moveTo(x, 0f)
                                lineTo(x + size.height * 0.48f, size.height)
                                x += gap
                            }
                        }
                        val stroke = Stroke(width = 1.dp.toPx())
                        onDrawBehind {
                            drawCircle(brush = b1, center = Offset(size.width * 0.92f, 0f), radius = size.width * 0.85f)
                            drawCircle(brush = b2, center = Offset(0f, size.height * 0.15f), radius = size.width * 0.7f)
                            drawPath(linesPath, lineColor, style = stroke)
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
        }
    }
}

/**
 * 零残影主题卡片：
 * - P5R：drawBehind 绘制黑色/红色硬投影 + 2dp 粗黑框 + 右上角血红小三角折角，无 layout offset 拖影；
 * - P3R：7dp 圆角 + 细蓝边 + 顶部右侧冰青-亮蓝斜切标；
 * - iOS：18dp 大圆角 + 轻微柔和投影。
 */
@Composable
fun MuCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    padding: Dp = 14.dp,
    showCornerAccent: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val mu = LocalMuColors.current
    val shape: Shape = RoundedCornerShape(mu.cardRadius)
    val bg = backgroundColor ?: mu.surface1
    val bd = borderColor ?: mu.line
    val shadowCol = if (borderColor != null && mu.style == ThemeStyle.P5R) borderColor else mu.hardShadowColor

    val base = if (mu.style == ThemeStyle.P5R && mu.hardShadowDp > 0.dp) {
        modifier
            .padding(end = mu.hardShadowDp, bottom = mu.hardShadowDp)
            .drawBehind {
                val sh = mu.hardShadowDp.toPx()
                drawRect(
                    color = shadowCol,
                    topLeft = Offset(sh, sh),
                    size = size
                )
            }
    } else if (mu.style == ThemeStyle.P3R) {
        modifier
            .padding(end = 2.dp, bottom = 3.dp)
            .drawBehind {
                val r = mu.cardRadius.toPx()
                drawRoundRect(
                    color = mu.hardShadowColor,
                    topLeft = Offset(1.dp.toPx(), 3.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(r, r)
                )
            }
    } else {
        modifier.shadow(2.dp, shape, ambientColor = Color.Black.copy(alpha = 0.06f))
    }

    val finalMod = base
        .clip(shape)
        .background(bg)
        .border(mu.cardBorderWidth, bd, shape)
        .drawWithContent {
            drawContent()
            if (showCornerAccent) {
                if (mu.style == ThemeStyle.P5R) {
                    // P5R 右上角 16dp 红三角折角
                    val tri = 16.dp.toPx()
                    val p = Path().apply {
                        moveTo(size.width - tri, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, tri)
                        close()
                    }
                    drawPath(p, mu.accent)
                } else if (mu.style == ThemeStyle.P3R) {
                    // P3R 右上角 28x5dp 冰青渐变斜切条
                    val w = 28.dp.toPx()
                    val h = 5.dp.toPx()
                    val rightPad = 10.dp.toPx()
                    val topPad = 9.dp.toPx()
                    val p = Path().apply {
                        moveTo(size.width - rightPad - w, topPad)
                        lineTo(size.width - rightPad, topPad)
                        lineTo(size.width - rightPad - 4.dp.toPx(), topPad + h)
                        lineTo(size.width - rightPad - w - 4.dp.toPx(), topPad + h)
                        close()
                    }
                    drawPath(p, Brush.horizontalGradient(listOf(mu.accent, mu.p3Cyan)))
                }
            }
        }
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (mu.style) {
            ThemeStyle.P5R -> {
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
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            ThemeStyle.P3R -> {
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
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            ThemeStyle.IOS -> {
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
        ThemeStyle.IOS -> RoundedCornerShape(12.dp)
        ThemeStyle.P3R -> RoundedCornerShape(6.dp)
        ThemeStyle.P5R -> RoundedCornerShape(0.dp)
    }
    val containerBg = when (mu.style) {
        ThemeStyle.P5R -> mu.chrome
        ThemeStyle.P3R -> mu.p3Deep
        ThemeStyle.IOS -> mu.surface3
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerBg)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items.forEach { (key, label) ->
            val isOn = key == selected
            val bg by animateColorAsState(
                targetValue = when {
                    isOn && mu.style == ThemeStyle.P5R -> mu.accent
                    isOn && mu.style == ThemeStyle.P3R -> Color.White
                    isOn -> mu.surface1
                    else -> Color.Transparent
                },
                animationSpec = tween(if (mu.style == ThemeStyle.IOS) 240 else 140),
                label = "segBg"
            )
            val txtColor = when {
                isOn && mu.style == ThemeStyle.P5R -> Color.White
                isOn && mu.style == ThemeStyle.P3R -> mu.p3Deep
                isOn -> mu.ink
                mu.style == ThemeStyle.P5R -> mu.chromeSoft
                mu.style == ThemeStyle.P3R -> Color(0xFFB8EAFF)
                else -> mu.ink2
            }
            val itemShape = when (mu.style) {
                ThemeStyle.IOS -> RoundedCornerShape(9.dp)
                ThemeStyle.P3R -> if (isOn) parallelogramShape(0.18f) else RoundedCornerShape(4.dp)
                ThemeStyle.P5R -> RoundedCornerShape(0.dp)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(itemShape)
                    .background(bg)
                    .clickable { onSelect(key) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = txtColor,
                    fontSize = 13.sp,
                    fontWeight = if (isOn) FontWeight.ExtraBold else FontWeight.SemiBold,
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
    val shape = when (mu.style) {
        ThemeStyle.P5R -> RoundedCornerShape(0.dp)
        ThemeStyle.P3R -> parallelogramShape(0.18f)
        ThemeStyle.IOS -> RoundedCornerShape(999.dp)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .padding(horizontal = if (mu.style == ThemeStyle.P3R) 9.dp else 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
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
        val showIndexStrip = mu.style != ThemeStyle.IOS
        val stripColor = when {
            overdueDays > 0 && !isDone -> mu.danger
            isDone -> mu.ink3
            mu.style == ThemeStyle.P3R -> mu.accent
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
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showIndexStrip) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(24.dp)
                            .background(stripColor),
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
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    val chkShape = when (mu.style) {
                        ThemeStyle.IOS -> CircleShape
                        ThemeStyle.P3R -> beveledCornerShape(0.24f)
                        ThemeStyle.P5R -> RoundedCornerShape(0.dp)
                    }
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(22.dp)
                            .clip(chkShape)
                            .background(if (isDone) mu.accent else Color.Transparent)
                            .border(
                                if (mu.style == ThemeStyle.P5R) 2.dp else 1.5.dp,
                                if (isDone) mu.accent else if (mu.style == ThemeStyle.P5R) mu.ink else mu.ink3,
                                chkShape
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleDone()
                            },
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
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                                .clip(RoundedCornerShape(if (mu.style == ThemeStyle.P5R) 0.dp else 3.dp))
                                                .background(if (st.done) mu.accent else Color.Transparent)
                                                .border(1.2.dp, if (st.done) mu.accent else mu.ink3, RoundedCornerShape(if (mu.style == ThemeStyle.P5R) 0.dp else 3.dp)),
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
                        TextButton(
                            onClick = onPostpone,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("明天", fontSize = 11.sp, color = mu.accentInk, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
