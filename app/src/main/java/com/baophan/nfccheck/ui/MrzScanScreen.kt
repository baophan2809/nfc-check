package com.baophan.nfccheck.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.baophan.nfccheck.idcard.CardAccessKey
import com.baophan.nfccheck.idcard.MrzUtil
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MrzScanScreen(onDone: (CardAccessKey) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var manual by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Đưa 3 dòng MRZ ở mặt sau CCCD vào trong khung.") }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        if (!it) manual = true
    }
    LaunchedEffect(Unit) { if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quét MRZ mặt sau") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
                },
                actions = {
                    TextButton(onClick = { manual = !manual }) {
                        Text(if (manual) "Dùng camera" else "Nhập tay")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (manual || !hasPermission) {
                Column(Modifier.padding(16.dp)) { ManualEntry(onDone) }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    CameraScanner(
                        onDetected = { key ->
                            if (!detected) {
                                detected = true
                                status = "Đã nhận diện MRZ, đang mở trình đọc chip…"
                                onDone(key)
                            }
                        }
                    )
                    ScannerOverlay(detected = detected)
                }
                Column(Modifier.padding(16.dp)) {
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (detected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Mẹo: để đủ sáng, đặt thẻ phẳng, lấp đầy bề ngang khung. Không được thì bấm \"Nhập tay\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Lớp phủ: làm tối ngoài khung và vẽ viền khung canh MRZ (vàng khi đã nhận diện). */
@Composable
private fun ScannerOverlay(detected: Boolean) {
    val border = if (detected) Color(0xFFFFC53D) else Color.White
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val boxW = w * 0.9f
        val boxH = boxW * 0.62f            // dải MRZ 3 dòng khá rộng và thấp
        val left = (w - boxW) / 2f
        val top = (h - boxH) / 2f
        val scrim = Color(0xCC0E1116)
        // 4 vùng tối quanh khung
        drawRect(scrim, Offset(0f, 0f), Size(w, top))
        drawRect(scrim, Offset(0f, top + boxH), Size(w, h - top - boxH))
        drawRect(scrim, Offset(0f, top), Size(left, boxH))
        drawRect(scrim, Offset(left + boxW, top), Size(w - left - boxW, boxH))
        // viền khung
        drawRoundRect(
            color = border,
            topLeft = Offset(left, top),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 6f)
        )
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun CameraScanner(onDetected: (CardAccessKey) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var delivered by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            recognizer.close()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val selector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            android.util.Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { proxy ->
                    if (delivered) { proxy.close(); return@setAnalyzer }
                    try {
                        val band = cropCenterBand(proxy)
                        if (band != null) {
                            val input = InputImage.fromBitmap(band, 0)
                            recognizer.process(input)
                                .addOnSuccessListener { visionText ->
                                    if (!delivered) {
                                        val parsed = MrzUtil.detect(visionText.text)
                                        if (parsed != null) {
                                            delivered = true
                                            onDetected(
                                                CardAccessKey(
                                                    parsed.documentNumber,
                                                    parsed.dob,
                                                    parsed.expiry
                                                )
                                            )
                                        }
                                    }
                                }
                                .addOnCompleteListener { proxy.close() }
                        } else proxy.close()
                    } catch (_: Throwable) {
                        proxy.close()
                    }
                }

                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

/** Xoay ảnh về đúng chiều rồi cắt lấy dải giữa (nơi đặt MRZ) để OCR chính xác hơn. */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun cropCenterBand(proxy: ImageProxy): Bitmap? {
    val raw = proxy.toBitmap()
    val rot = proxy.imageInfo.rotationDegrees
    val upright = if (rot != 0) {
        val m = Matrix().apply { postRotate(rot.toFloat()) }
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    } else raw
    val h = upright.height
    val w = upright.width
    if (h <= 0 || w <= 0) return null
    val top = (h * 0.30f).toInt()
    val bandH = min((h * 0.42f).toInt(), h - top)
    if (bandH <= 0) return upright
    return Bitmap.createBitmap(upright, 0, top, w, bandH)
}

@Composable
private fun ManualEntry(onDone: (CardAccessKey) -> Unit) {
    var doc by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var exp by remember { mutableStateOf("") }
    SectionCard(title = "Nhập khóa mở chip theo MRZ") {
        Text(
            "Lấy từ dòng MRZ ở mặt sau. Ngày theo định dạng YYMMDD (ví dụ 01/01/2000 → 000101).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(doc, { doc = it }, label = { Text("Số thẻ / CCCD (document number)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            dob, { if (it.length <= 6 && it.all { c -> c.isDigit() }) dob = it },
            label = { Text("Ngày sinh YYMMDD") }, singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            exp, { if (it.length <= 6 && it.all { c -> c.isDigit() }) exp = it },
            label = { Text("Ngày hết hạn YYMMDD") }, singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onDone(CardAccessKey(doc.trim().uppercase(), dob.trim(), exp.trim())) },
            enabled = doc.isNotBlank() && dob.length == 6 && exp.length == 6,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Dùng khóa này") }
    }
}
