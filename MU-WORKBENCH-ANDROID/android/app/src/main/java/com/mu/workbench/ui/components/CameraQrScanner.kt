package com.mu.workbench.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.mu.workbench.data.ThemeStyle
import com.mu.workbench.ui.theme.LocalMuColors
import com.mu.workbench.ui.theme.UiSkin
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Composable
fun CameraQrScanner(
    paused: Boolean,
    zoomRatio: Float,
    onZoomChange: (Float) -> Unit,
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mu = LocalMuColors.current

    var hasPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPerm = granted
    }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var maxHwZoom by remember { mutableFloatStateOf(4f) }
    var hasFlash by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasPerm) permLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(zoomRatio, cameraRef) {
        val cam = cameraRef ?: return@LaunchedEffect
        val maxZ = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 4f
        maxHwZoom = maxZ
        cam.cameraControl.setZoomRatio(zoomRatio.coerceIn(1f, maxZ))
    }

    LaunchedEffect(torchOn, cameraRef) {
        val cam = cameraRef ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) {
            cam.cameraControl.enableTorch(torchOn)
        }
    }

    if (!hasPerm) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(270.dp)
                .clip(RoundedCornerShape(mu.cardRadius))
                .background(mu.surface2)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("需要相机权限以扫描课堂签到码", color = mu.ink2, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("授权相机")
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(mu.cardRadius))
                .background(Color.Black)
                .pointerInput(cameraRef, zoomRatio, maxHwZoom) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1f) {
                            val upper = maxHwZoom.coerceIn(1f, 8f)
                            onZoomChange((zoomRatio * zoomChange).coerceIn(1f, upper))
                        }
                    }
                }
                .pointerInput(cameraRef, previewViewRef) {
                    detectTapGestures { offset ->
                        val cam = cameraRef ?: return@detectTapGestures
                        val pv = previewViewRef ?: return@detectTapGestures
                        triggerFocus(cam, pv, offset.x, offset.y)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
            val mainHandler = remember { Handler(Looper.getMainLooper()) }
            val pausedState by rememberUpdatedState(paused)
            val zoomState by rememberUpdatedState(zoomRatio)
            val maxHwZoomState by rememberUpdatedState(maxHwZoom)
            val onResultState by rememberUpdatedState(onResult)
            var providerRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
            var disposed by remember { mutableStateOf(false) }

            DisposableEffect(Unit) {
                onDispose {
                    disposed = true
                    try {
                        cameraRef?.cameraControl?.enableTorch(false)
                    } catch (_: Throwable) {
                    }
                    try {
                        providerRef?.unbindAll()
                    } catch (_: Throwable) {
                    }
                    analyzerExecutor.shutdownNow()
                }
            }

            AndroidView(
                factory = { context ->
                    val pv = PreviewView(context).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    previewViewRef = pv
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        if (disposed || analyzerExecutor.isShutdown) return@addListener
                        try {
                            val provider = future.get()
                            providerRef = provider
                            val previewResSelector = ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(1280, 720),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                    )
                                )
                                .build()
                            val analysisResSelector = ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(1280, 720),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                    )
                                )
                                .build()
                            val preview = Preview.Builder()
                                .setResolutionSelector(previewResSelector)
                                .build()
                                .also { it.surfaceProvider = pv.surfaceProvider }
                            val analysis = ImageAnalysis.Builder()
                                .setResolutionSelector(analysisResSelector)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            val decoder = FastQrDecoder()
                            var lastHitMs = 0L

                            analysis.setAnalyzer(analyzerExecutor) { proxy ->
                                try {
                                    if (!disposed && !pausedState) {
                                        val extraDigitalCrop = if (zoomState > maxHwZoomState) {
                                            (zoomState / maxHwZoomState.coerceAtLeast(1f)).coerceIn(1f, 3f)
                                        } else 1f
                                        val nowMs = System.currentTimeMillis()
                                        if (nowMs - lastHitMs > 600L) {
                                            decoder.decode(proxy, extraDigitalCrop)?.let { text ->
                                                lastHitMs = nowMs
                                                mainHandler.post { onResultState(text) }
                                            }
                                        }
                                    }
                                } finally {
                                    proxy.close()
                                }
                            }
                            provider.unbindAll()
                            val cam = provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                            cameraRef = cam
                            hasFlash = cam.cameraInfo.hasFlashUnit()
                            maxHwZoom = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 4f
                            cam.cameraControl.setZoomRatio(zoomState.coerceIn(1f, maxHwZoom))
                            pv.post {
                                if (!disposed && pv.width > 0 && pv.height > 0) {
                                    triggerFocus(cam, pv, pv.width / 2f, pv.height / 2f)
                                }
                            }
                        } catch (_: Throwable) {
                        }
                    }, ContextCompat.getMainExecutor(context))
                    pv
                },
                modifier = Modifier.fillMaxSize()
            )

            // 取景框线
            Box(
                modifier = Modifier
                    .size(185.dp)
                    .border(2.dp, if (paused) mu.ok else mu.accent, RoundedCornerShape(if (mu.skin == UiSkin.P5R) 0.dp else 12.dp))
            )

            // 右上角闪光灯按钮
            if (hasFlash) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(if (mu.skin == UiSkin.P5R) 0.dp else 999.dp))
                        .background(if (torchOn) mu.accent else Color.Black.copy(alpha = 0.65f))
                        .clickable { torchOn = !torchOn }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = if (torchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                        contentDescription = if (torchOn) "关闭闪光灯" else "开启闪光灯",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 1x / 2x / 3x / 4x 远距离教室变焦栏（防翘课远拍大屏用）
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(if (mu.skin == UiSkin.P5R) 0.dp else 999.dp))
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(1f, 2f, 3f, 4f).forEach { z ->
                    val on = kotlin.math.abs(zoomRatio - z) < 0.25f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(if (mu.skin == UiSkin.P5R) 0.dp else 999.dp))
                            .background(if (on) mu.accent else Color.Transparent)
                            .clickable {
                                onZoomChange(z)
                                val cam = cameraRef
                                val pv = previewViewRef
                                if (cam != null && pv != null && pv.width > 0 && pv.height > 0) {
                                    triggerFocus(cam, pv, pv.width / 2f, pv.height / 2f)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${z.toInt()}×",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val sliderUpper = maxHwZoom.coerceIn(2f, 8f)
            Text("变焦 ${"%.1f".format(zoomRatio)}×", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = mu.ink2)
            Slider(
                value = zoomRatio.coerceIn(1f, sliderUpper),
                onValueChange = onZoomChange,
                valueRange = 1f..sliderUpper,
                modifier = Modifier.weight(1f)
            )
            Text("点画面可对焦", fontSize = 11.sp, color = mu.ink3)
        }
    }
}

private fun triggerFocus(cam: Camera, pv: PreviewView, x: Float, y: Float) {
    try {
        val pt = pv.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(pt, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    } catch (_: Throwable) {
    }
}

/**
 * 复用缓冲区的低延迟 QR 解码器：
 * 1. 每帧零大数组分配，避免 1080p 连续扫码触发 GC 卡顿；
 * 2. 先用 GlobalHistogramBinarizer 快速扫中心正方形（低耗时），再扫 2× 降采样全帧（抗屏幕摩尔纹/斜拍）；
 * 3. 奇偶帧交替补充 TRY_HARDER（远距小码）与反色解码（暗底白码大屏），兼顾高帧率与检出率。
 */
private class FastQrDecoder {
    private val reader = QRCodeReader()
    private val fastHints = mapOf(
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )
    private val hardHints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    private var yData = ByteArray(0)
    private var rowBuf = ByteArray(0)
    private var halfData = ByteArray(0)
    private var frameCounter = 0

    fun decode(proxy: ImageProxy, extraDigitalCrop: Float): String? {
        val plane = proxy.planes.firstOrNull() ?: return null
        val w = proxy.width
        val h = proxy.height
        if (w <= 0 || h <= 0) return null

        extractLuma(plane, w, h)
        val frameIdx = frameCounter++

        // 中心正方形裁剪（兼容横竖屏旋转方向）
        val minDim = minOf(w, h)
        val cropFactor = (extraDigitalCrop.coerceAtLeast(1f) * 1.35f)
        val side = (minDim / cropFactor).toInt().coerceIn(80, minDim)
        val cLeft = (w - side) / 2
        val cTop = (h - side) / 2
        val centerSrc = PlanarYUVLuminanceSource(yData, w, h, cLeft, cTop, side, side, false)

        // 1. 中心区快速通道（GlobalHistogramBinarizer，~5ms）
        decodeGlobal(centerSrc, fastHints)?.let { return it }

        // 2. 2× 降采样全帧（消除投影/显示器像素网格摩尔纹，并覆盖偏离中心的码）
        val hw = w / 2
        val hh = h / 2
        if (hw >= 80 && hh >= 80) {
            downsample2x(w, h, hw, hh)
            val halfSrc = PlanarYUVLuminanceSource(halfData, hw, hh, 0, 0, hw, hh, false)
            decodeHybrid(halfSrc, fastHints)?.let { return it }

            // 每 4 帧补充一次降采样反色解码
            if ((frameIdx and 3) == 1) {
                decodeHybrid(halfSrc.invert(), fastHints)?.let { return it }
            }
        }

        // 3. 奇偶帧交替做精细通道 / 反色通道，保证高帧率下不掉帧
        if ((frameIdx and 1) == 0) {
            decodeHybrid(centerSrc, hardHints)?.let { return it }
        } else {
            decodeHybrid(centerSrc.invert(), hardHints)?.let { return it }
        }

        return null
    }

    private fun extractLuma(plane: ImageProxy.PlaneProxy, w: Int, h: Int) {
        val needed = w * h
        if (yData.size < needed) yData = ByteArray(needed)
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        buf.rewind()
        if (rowStride == w && pixelStride == 1) {
            buf.get(yData, 0, minOf(buf.remaining(), needed))
            return
        }
        if (rowBuf.size < rowStride) rowBuf = ByteArray(rowStride)
        for (row in 0 until h) {
            val toRead = minOf(rowStride, buf.remaining())
            if (toRead <= 0) break
            buf.get(rowBuf, 0, toRead)
            val dstOff = row * w
            if (pixelStride == 1) {
                System.arraycopy(rowBuf, 0, yData, dstOff, minOf(w, toRead))
            } else {
                var c = 0
                while (c < w && c * pixelStride < toRead) {
                    yData[dstOff + c] = rowBuf[c * pixelStride]
                    c++
                }
            }
        }
    }

    private fun downsample2x(w: Int, h: Int, hw: Int, hh: Int) {
        val needed = hw * hh
        if (halfData.size < needed) halfData = ByteArray(needed)
        for (y in 0 until hh) {
            val srcRow = (y shl 1) * w
            val dstRow = y * hw
            for (x in 0 until hw) {
                halfData[dstRow + x] = yData[srcRow + (x shl 1)]
            }
        }
    }

    private fun decodeGlobal(src: LuminanceSource, hints: Map<DecodeHintType, Any>): String? {
        return try {
            reader.decode(BinaryBitmap(GlobalHistogramBinarizer(src)), hints).text
        } catch (_: Throwable) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun decodeHybrid(src: LuminanceSource, hints: Map<DecodeHintType, Any>): String? {
        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(src)), hints).text
        } catch (_: Throwable) {
            null
        } finally {
            reader.reset()
        }
    }
}
