package com.baophan.nfccheck.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baophan.nfccheck.model.ScanResult
import com.baophan.nfccheck.model.ScanType
import com.baophan.nfccheck.storage.HistoryIndexEntry
import com.baophan.nfccheck.storage.UnlockResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit) {
    var refresh by remember { mutableStateOf(0) }
    val entries = remember(refresh) { vm.history.list() }
    var opened by remember { mutableStateOf<ScanResult?>(null) }
    var unlockFor by remember { mutableStateOf<HistoryIndexEntry?>(null) }
    var snack by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val snackState = remember { SnackbarHostState() }

    LaunchedEffect(snack) {
        snack?.let { snackState.showSnackbar(it); snack = null }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackState) },
        topBar = {
            TopAppBar(
                title = { Text(if (opened == null) "Lịch sử" else "Chi tiết") },
                navigationIcon = {
                    IconButton(onClick = { if (opened != null) opened = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    if (opened == null && entries.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Xóa sạch")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize()) {
            val detail = opened
            if (detail != null) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ResultView(
                        result = detail,
                        strictDelete = true,
                        onWrongDelete = {
                            vm.history.delete(detail.id)
                            opened = null
                            refresh++
                            snack = "Nhập sai — đã xóa bản ghi này."
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { vm.history.delete(detail.id); opened = null; refresh++ },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Xóa bản ghi này") }
                    Spacer(Modifier.height(24.dp))
                }
            } else if (entries.isEmpty()) {
                Text(
                    "Chưa có bản ghi nào.",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn {
                    items(entries, key = { it.id }) { e ->
                        HistoryRow(e) {
                            if (e.gated) {
                                unlockFor = e
                            } else {
                                opened = vm.history.load(e.id)
                                if (opened == null) snack = "Không mở được bản ghi."
                            }
                        }
                    }
                }
            }
        }
    }

    unlockFor?.let { entry ->
        Last4Dialog(
            onConfirm = { entered ->
                when (val r = vm.history.unlock(entry.id, entered)) {
                    is UnlockResult.Success -> { opened = r.result; unlockFor = null }
                    UnlockResult.WrongDeleted -> {
                        unlockFor = null; refresh++
                        snack = "Nhập sai — đã xóa bản ghi này."
                    }
                    UnlockResult.NotFound -> { unlockFor = null; snack = "Không tìm thấy bản ghi." }
                }
            },
            onDismiss = { unlockFor = null }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = {
                TextButton(onClick = { vm.history.clearAll(); confirmClear = false; refresh++ }) {
                    Text("Xóa hết")
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Hủy") } },
            title = { Text("Xóa toàn bộ lịch sử?") },
            text = { Text("Mọi bản ghi đã mã hóa sẽ bị xóa vĩnh viễn khỏi máy.") }
        )
    }
}

@Composable
private fun HistoryRow(e: HistoryIndexEntry, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${typeLabel(e.type)} · ${e.scheme}",
                    style = MaterialTheme.typography.titleSmall
                )
                e.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(fmtTime(e.timeMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (e.gated) Text("🔒", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Last4Dialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.length == 4) { Text("Mở") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
        title = { Text("Nhập 4 số cuối") },
        text = {
            Column {
                Text("Nhập 4 số cuối in trên thẻ để mở bản ghi. Nhập sai 1 lần là bản ghi này bị xóa vĩnh viễn.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) text = it },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    label = { Text("4 số cuối") }
                )
            }
        }
    )
}

private fun typeLabel(t: ScanType) = when (t) {
    ScanType.EMV -> "Thẻ thanh toán"
    ScanType.IDCARD -> "CCCD"
    ScanType.TAG -> "Thẻ NFC"
}

private fun fmtTime(t: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    return sdf.format(Date(t))
}
