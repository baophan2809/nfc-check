package com.baophan.nfccheck.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baophan.nfccheck.model.ScanType
import com.baophan.nfccheck.storage.Exporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    nfcSupported: Boolean,
    nfcEnabled: Boolean,
    onOpenHistory: () -> Unit,
    onOpenMrz: () -> Unit
) {
    val state = vm.state
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFC Check") },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "Lịch sử")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ModeSelector(vm)

            if (!nfcSupported) {
                Warn("Máy này không có NFC nên không đọc được thẻ.")
            } else if (!nfcEnabled) {
                Warn("NFC đang tắt. Bật NFC trong Cài đặt rồi quay lại.")
            }

            if (vm.mode == ScanMode.IDCARD) {
                CccdKeyPanel(vm, onOpenMrz)
            }

            Spacer(Modifier.height(8.dp))

            when (val s = state) {
                is ReaderState.Idle -> TapPrompt(vm.mode)
                is ReaderState.Reading -> ReadingPrompt()
                is ReaderState.Error -> {
                    ErrorPanel(s.message) { vm.reset() }
                }
                is ReaderState.Done -> {
                    ResultView(
                        result = s.result,
                        strictDelete = false,
                        onWrongDelete = {}
                    )
                    Spacer(Modifier.height(12.dp))
                    ExportRow(s.result)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Quét thẻ tiếp")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(vm: MainViewModel) {
    val modes = listOf(
        ScanMode.AUTO to "Tự động",
        ScanMode.EMV to "Thẻ thanh toán",
        ScanMode.IDCARD to "CCCD",
        ScanMode.TAG to "Thẻ khác"
    )
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("Chế độ đọc", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRowChips(modes.map { it.second }, selectedIndex = modes.indexOfFirst { it.first == vm.mode }) { idx ->
            vm.selectMode(modes[idx].first)
        }
    }
}

@Composable
private fun FlowRowChips(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            FilterChip(
                selected = i == selectedIndex,
                onClick = { onSelect(i) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CccdKeyPanel(vm: MainViewModel, onOpenMrz: () -> Unit) {
    SectionCard {
        if (vm.pendingKey == null) {
            Text("Để đọc chip CCCD, trước tiên quét vùng MRZ ở mặt sau thẻ bằng camera.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenMrz, modifier = Modifier.fillMaxWidth()) { Text("Quét MRZ mặt sau") }
        } else {
            Text("Đã có khóa mở chip. Giờ áp mặt sau CCCD vào lưng máy và giữ yên.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.setKey(null) }, modifier = Modifier.fillMaxWidth()) { Text("Quét lại MRZ") }
        }
    }
}

@Composable
private fun TapPrompt(mode: ScanMode) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Contactless,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            when (mode) {
                ScanMode.IDCARD -> "Áp mặt sau CCCD vào lưng máy"
                ScanMode.EMV -> "Áp thẻ thanh toán vào lưng máy"
                ScanMode.TAG -> "Áp thẻ/tag NFC vào lưng máy"
                ScanMode.AUTO -> "Áp thẻ vào lưng máy"
            },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Giữ yên vài giây cho tới khi đọc xong.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReadingPrompt() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Đang đọc… giữ yên thẻ", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Không đọc được", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Thử lại") }
        }
    }
}

@Composable
private fun Warn(message: String) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
    ) {
        Text(message, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExportRow(result: com.baophan.nfccheck.model.ScanResult) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var mask by remember { mutableStateOf(true) }
    var pendingContent by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val content = pendingContent
        if (uri != null && content != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            } catch (_: Exception) {
            }
        }
    }

    SectionCard(title = "Xuất kết quả") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = mask, onCheckedChange = { mask = it })
            Text("Che số thẻ khi xuất", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    pendingContent = Exporter.toJson(result, mask)
                    launcher.launch("nfccheck_${result.timeMillis}.json")
                },
                modifier = Modifier.weight(1f)
            ) { Text("JSON") }
            OutlinedButton(
                onClick = {
                    pendingContent = Exporter.toText(result, mask)
                    launcher.launch("nfccheck_${result.timeMillis}.txt")
                },
                modifier = Modifier.weight(1f)
            ) { Text("TXT") }
        }
    }
}
