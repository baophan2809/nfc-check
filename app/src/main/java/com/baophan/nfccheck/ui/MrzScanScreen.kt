package com.baophan.nfccheck.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.jmrtd.lds.icao.MRZInfo
import java.util.concurrent.Executors

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
    var status by remember { mutableStateOf("Đưa vùng MRZ (3 dòng ký tự ở mặt sau) vào khung hình.") }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        if (!it) manual = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quét MRZ mặt sau") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại")
                    }
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
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            if (manual || !hasPermission) {
                ManualEntry(onDone)
            } else {
                CameraPreview(
                    onMrz = { mrz ->
                        val key = parseMrz(mrz)
                        if (key != null) onDone(key) else status = "Chưa đọc rõ MRZ, giữ máy yên hơn một chút."
                    },
                    onStatus = { status = it }
                )
                Spacer(Modifier.height(12.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    "MRZ không ra? Bấm \"Nhập tay\" để gõ số thẻ, ngày sinh và ngày hết hạn.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onMrz: (String) -> Unit, onStatus: (String) -> Unit) {
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
        modifier = Modifier.fillMaxWidth().height(360.dp),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy: ImageProxy ->
                    if (delivered) { proxy.close(); return@setAnalyzer }
                    val mediaImage = proxy.image
                    if (mediaImage == null) { proxy.close(); return@setAnalyzer }
                    val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                    recognizer.process(input)
                        .addOnSuccessListener { visionText ->
                            val mrz = MrzUtil.extract(visionText.text)
                            if (mrz != null && !delivered) {
                                delivered = true
                                onMrz(mrz)
                            }
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Exception) {
                    onStatus("Không mở được camera: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
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

private fun parseMrz(mrz: String): CardAccessKey? {
    return try {
        val info = MRZInfo(mrz)
        val doc = info.documentNumber?.trim('<', ' ') ?: return null
        val dob = info.dateOfBirth ?: return null
        val exp = info.dateOfExpiry ?: return null
        if (doc.isBlank() || dob.length < 6 || exp.length < 6) null
        else CardAccessKey(doc, dob, exp)
    } catch (_: Exception) {
        null
    }
}
