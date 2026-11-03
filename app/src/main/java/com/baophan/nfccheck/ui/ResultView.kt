package com.baophan.nfccheck.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baophan.nfccheck.emv.EmvCard
import com.baophan.nfccheck.idcard.IdCardData
import com.baophan.nfccheck.model.ScanResult
import com.baophan.nfccheck.model.ScanType
import com.baophan.nfccheck.nfctag.NfcTagData
import com.baophan.nfccheck.util.Mask

@Composable
fun ResultView(
    result: ScanResult,
    strictDelete: Boolean,
    onWrongDelete: () -> Unit
) {
    var revealed by remember(result.id) { mutableStateOf(result.last4 == null) }
    var showDialog by remember(result.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        when (result.type) {
            ScanType.EMV -> result.emv?.let {
                EmvHeader(it, revealed) { showDialog = true }
                EmvTabs(it, revealed)
            }
            ScanType.IDCARD -> result.idCard?.let {
                IdCardHeader(it, revealed) { showDialog = true }
                IdCardBody(it, revealed)
            }
            ScanType.TAG -> result.tag?.let { TagBody(it) }
        }
    }

    if (showDialog) {
        RevealDialog(
            strictDelete = strictDelete,
            onConfirm = { entered ->
                if (entered.filter { c -> c.isDigit() } == result.last4) {
                    revealed = true
                    showDialog = false
                } else {
                    showDialog = false
                    if (strictDelete) onWrongDelete()
                    else revealed = false
                }
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun RevealDialog(
    strictDelete: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.length == 4) {
                Text("Xem")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
        title = { Text("Nhập 4 số cuối") },
        text = {
            Column {
                Text(
                    if (strictDelete)
                        "Nhập 4 số cuối IN TRÊN thẻ để mở. Lưu ý: nhập sai 1 lần là bản ghi này bị xóa vĩnh viễn."
                    else
                        "Nhập 4 số cuối in trên thẻ để hiện đầy đủ số."
                )
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

// ---------- EMV ----------

@Composable
private fun EmvHeader(card: EmvCard, revealed: Boolean, onReveal: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).height(200.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(card.scheme, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                card.issuer?.bank?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            }
            val pan = card.pan
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (pan == null) "—" else if (revealed) Mask.format(pan) else Mask.maskAll(pan),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                if (pan != null && !revealed) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onReveal) {
                        Icon(Icons.Filled.Visibility, contentDescription = "Xem đầy đủ")
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Chủ thẻ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(card.cardholderName ?: "—", style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Hết hạn", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(card.expiry ?: "—", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
    if (!revealed && card.pan != null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Số thẻ đang được che. Chạm biểu tượng con mắt để hiện.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmvTabs(card: EmvCard, revealed: Boolean) {
    val tabs = listOf("Thông tin", "Giao dịch", "Kỹ thuật", "LOG")
    var tab by remember { mutableStateOf(0) }
    TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
        tabs.forEachIndexed { i, t ->
            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, style = MaterialTheme.typography.labelMedium) })
        }
    }
    Spacer(Modifier.height(8.dp))
    when (tab) {
        0 -> SectionCard {
            InfoRow("Tổ chức thẻ", card.scheme)
            InfoRow("Ngân hàng phát hành", card.issuer?.bank)
            InfoRow("BIN (đầu số)", card.issuer?.bin, mono = true)
            InfoRow("Hiệu lực từ", card.effectiveDate)
            InfoRow("Hết hạn", card.expiry)
            InfoRow("Mã nước phát hành", card.issuerCountry, mono = true)
            InfoRow("Số thứ tự PAN", card.panSequence, mono = true)
            InfoRow("Số lần nhập PIN còn lại", card.pinTryCounter?.toString())
            InfoRow("ATC (bộ đếm giao dịch)", card.atc?.toString())
            InfoRow("AID đang chọn", card.selectedAid, mono = true)
            if (card.applications.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Ứng dụng trên thẻ:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                card.applications.forEach {
                    InfoRow(it.label ?: it.preferredName ?: it.scheme, it.aid, mono = true)
                }
            }
        }
        1 -> if (card.txLog.isEmpty()) {
            SectionCard { Text("Thẻ không lưu nhật ký giao dịch trên chip, hoặc không đọc được.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else SectionCard {
            card.txLog.forEach {
                InfoRow(it.date ?: "?", "${it.amount ?: "?"} ${it.currency ?: ""}  ${it.type ?: ""}")
            }
        }
        2 -> SectionCard {
            if (!revealed) Text("Một số giá trị có thể chứa số thẻ. Đã che cho tới khi bạn mở.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            card.tags.forEach {
                val show = if (revealed) it.valueHex else maskTechValue(it.tag, it.valueHex)
                InfoRow("${it.tag} ${it.name ?: ""}".trim(), show, mono = true)
            }
        }
        3 -> SectionCard {
            card.apduLog.forEach {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text("[${it.label}]  → ${it.sw}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    val hs = rememberScrollState()
                    MonoScroll("C: ${it.command}", hs)
                    MonoScroll("R: ${it.response}", hs)
                }
            }
        }
    }
}

private fun maskTechValue(tag: String, hex: String): String {
    // Che các tag có thể lộ số thẻ: 57 (track2), 5A (PAN), 9F1F, 56
    val sensitive = setOf("57", "5A", "9F1F", "56", "9F20")
    return if (tag.uppercase() in sensitive) "•• (đã che)" else hex
}

@Composable
private fun MonoScroll(text: String, scroll: ScrollState) {
    Text(
        text,
        modifier = Modifier.horizontalScroll(scroll),
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1
    )
}

// ---------- CCCD ----------

@Composable
private fun IdCardHeader(d: IdCardData, revealed: Boolean, onReveal: () -> Unit) {
    SectionCard(title = "CCCD gắn chip (${d.protocol})") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            d.faceJpeg?.let { bytes ->
                val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                bmp?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Ảnh chân dung",
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
                }
            }
            Column {
                Text(d.fullName ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val num = d.documentNumber
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (num == null) "—" else if (revealed) Mask.format(num) else Mask.maskAll(num),
                        fontFamily = FontFamily.Monospace
                    )
                    if (num != null && !revealed) {
                        IconButton(onClick = onReveal) { Icon(Icons.Filled.Visibility, "Xem") }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdCardBody(d: IdCardData, revealed: Boolean) {
    SectionCard {
        InfoRow("Ngày sinh", d.dateOfBirth)
        InfoRow("Giới tính", d.sex)
        InfoRow("Quốc tịch", d.nationality)
        InfoRow("Nơi cấp / phát hành", d.issuingState)
        InfoRow("Ngày hết hạn", d.dateOfExpiry)
        InfoRow("Số định danh", if (revealed) d.personalNumber else d.personalNumber?.let { "•• (đã che)" })
        InfoRow("Nhóm dữ liệu đọc được", d.dataGroupsRead.joinToString(", "))
    }
    d.extraText?.let {
        SectionCard(title = "Thông tin thêm (DG13)") {
            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

// ---------- NFC tag ----------

@Composable
private fun TagBody(t: NfcTagData) {
    SectionCard(title = "Thẻ / Tag NFC") {
        InfoRow("UID", t.uid, mono = true)
        InfoRow("Loại chip", t.chipType)
        InfoRow("Công nghệ", t.techList.joinToString(", "))
        InfoRow("ATQA", t.atqa, mono = true)
        InfoRow("SAK", t.sak, mono = true)
        InfoRow("Dung lượng NDEF tối đa", t.maxSize?.let { "$it byte" })
        InfoRow("Có thể ghi", t.writable?.let { if (it) "Có" else "Không" })
        InfoRow("Loại NDEF", t.ndefType)
    }
    if (t.ndefRecords.isNotEmpty()) {
        SectionCard(title = "Nội dung NDEF") {
            t.ndefRecords.forEach { InfoRow(it.type, it.payload) }
        }
    }
}
