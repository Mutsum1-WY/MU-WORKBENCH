package com.mu.workbench.ui.components

import android.Manifest
import android.content.pm.PackageManager
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.mu.workbench.data.ThemeStyle
import com.mu.workbench.ui.theme.LocalMuColors
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

    LaunchedEffect(Unit) {
        if (!hasPerm) permLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(zoomRatio, cameraRef) {
        val cam = cameraRef ?: return@LaunchedEffect
        val maxZ = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 4f
        maxHwZoom = maxZ
        cam.cameraControl.setZoomRatio(zoomRatio.coerceIn(1f, maxZ))
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
                .pointerInput(cameraRef, zoomRatio) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1f) {
                            onZoomChange((zoomRatio * zoomChange).coerceIn(1f, 4f))
                        }
                    }
                }
                .pointerInput(cameraRef, previewViewRef) {
                    detectTapGestures { offset ->
                        val cam = cameraRef ?: return@detectTapGestures
                        val pv = previewViewRef ?: return@detectTapGestures
                        try {
                            val pt = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(pt, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            cam.cameraControl.startFocusAndMetering(action)
                        } catch (_: Throwable) {
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
            val pausedState by rememberUpdatedState(paused)
            val zoomState by rememberUpdatedState(zoomRatio)
            val maxHwZoomState by rememberUpdatedState(maxHwZoom)
            val onResultState by rememberUpdatedState(onResult)

            DisposableEffect(Unit) {
                onDispose { analyzerExecutor.shutdown() }
            }

            AndroidView(
                factory = { context ->
                    val pv = PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    previewViewRef = pv
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        try {
                            val provider = future.get()
                            val resSelector = ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(1920, 1080),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                    )
                                )
                                .build()
                            val preview = Preview.Builder()
                                .setResolutionSelector(resSelector)
                                .build()
                                .also { it.surfaceProvider = pv.surfaceProvider }
                            val analysis = ImageAnalysis.Builder()
                                .setResolutionSelector(resSelector)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            val reader = MultiFormatReader().apply {
                                setHints(
                                    mapOf(
                                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                        DecodeHintType.TRY_HARDER to true,
                                        DecodeHintType.CHARACTER_SET to "UTF-8"
                                    )
                                )
                            }
                            analysis.setAnalyzer(analyzerExecutor) { proxy ->
                                try {
                                    if (!pausedState) {
                                        val extraDigitalCrop = if (zoomState > maxHwZoomState) {
                                            (zoomState / maxHwZoomState.coerceAtLeast(1f)).coerceIn(1f, 3f)
                                        } else 1f
                                        decodeProxy(proxy, reader, extraDigitalCrop)?.let { text ->
                                            onResultState(text)
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
                            maxHwZoom = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 4f
                            cam.cameraControl.setZoomRatio(zoomState.coerceIn(1f, maxHwZoom))
                        } catch (_: Throwable) {
                        }
                    }, ContextCompat.getMainExecutor(context))
                    pv
                },
                modifier = Modifier.fillMaxSize()
            )

            // 取景框线 + 点击对焦提示
            Box(
                modifier = Modifier
                    .size(185.dp)
                    .border(2.dp, if (paused) mu.ok else mu.accent, RoundedCornerShape(if (mu.style == ThemeStyle.P5R) 0.dp else 12.dp))
            )

            // 1x / 2x / 3x / 4x 远距离教室变焦栏（防翘课远拍大屏用）
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(if (mu.style == ThemeStyle.P5R) 0.dp else 999.dp))
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(1f, 2f, 3f, 4f).forEach { z ->
                    val on = kotlin.math.abs(zoomRatio - z) < 0.25f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(if (mu.style == ThemeStyle.P5R) 0.dp else 999.dp))
                            .background(if (on) mu.accent else Color.Transparent)
                            .clickable { onZoomChange(z) }
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
            Text("变焦 ${"%.1f".format(zoomRatio)}×", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = mu.ink2)
            Slider(
                value = zoomRatio,
                onValueChange = onZoomChange,
                valueRange = 1f..4f,
                modifier = Modifier.weight(1f)
            )
            Text("点画面可对焦", fontSize = 11.sp, color = mu.ink3)
        }
    }
}

private fun decodeProxy(
    proxy: ImageProxy,
    reader: MultiFormatReader,
    extraDigitalCrop: Float
): String? {
    val plane = proxy.planes.firstOrNull() ?: return null
    val w = proxy.width
    val h = proxy.height
    if (w <= 0 || h <= 0) return null

    // 按 rowStride 规整抽取紧凑 Y 通道（防止不同机型 stride padding 导致斜切错位）
    val yData = ByteArray(w * h)
    val buf = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    buf.rewind()
    if (rowStride == w && pixelStride == 1) {
        buf.get(yData, 0, minOf(buf.remaining(), yData.size))
    } else {
        val rowBuf = ByteArray(rowStride)
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

    fun tryDecodeCrop(left: Int, top: Int, cw: Int, ch: Int): String? {
        if (cw < 40 || ch < 40) return null
        val src = PlanarYUVLuminanceSource(yData, w, h, left, top, cw, ch, false)
        // 1. 常规黑白码
        try {
            return reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text
        } catch (_: Throwable) {
        } finally {
            reader.reset()
        }
        // 2. 反色码（大屏深色模式/白码黑底）
        try {
            return reader.decodeWithState(BinaryBitmap(HybridBinarizer(src.invert()))).text
        } catch (_: Throwable) {
        } finally {
            reader.reset()
        }
        return null
    }

    // 先扫中心裁剪区（远拍屏幕噪点更少），若无果再扫全帧
    val cropFactor = (extraDigitalCrop.coerceAtLeast(1f) * 1.35f)
    val cw = (w / cropFactor).toInt().coerceIn(80, w)
    val ch = (h / cropFactor).toInt().coerceIn(80, h)
    tryDecodeCrop((w - cw) / 2, (h - ch) / 2, cw, ch)?.let { return it }
    if (cw < w || ch < h) {
        tryDecodeCrop(0, 0, w, h)?.let { return it }
    }
    return null
}
