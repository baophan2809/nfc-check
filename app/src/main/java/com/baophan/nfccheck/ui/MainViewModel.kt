package com.baophan.nfccheck.ui

import android.app.Application
import android.nfc.Tag
import android.nfc.tech.IsoDep
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.baophan.nfccheck.emv.EmvCard
import com.baophan.nfccheck.emv.EmvReader
import com.baophan.nfccheck.idcard.CardAccessKey
import com.baophan.nfccheck.idcard.IdCardReader
import com.baophan.nfccheck.model.ScanResult
import com.baophan.nfccheck.model.ScanType
import com.baophan.nfccheck.nfctag.NfcTagReader
import com.baophan.nfccheck.storage.HistoryStore
import com.baophan.nfccheck.util.Mask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ScanMode { AUTO, EMV, IDCARD, TAG }

sealed class ReaderState {
    object Idle : ReaderState()
    object Reading : ReaderState()
    data class Done(val result: ScanResult) : ReaderState()
    data class Error(val message: String) : ReaderState()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val history = HistoryStore(app)

    var mode by mutableStateOf(ScanMode.AUTO)
        private set
    var state by mutableStateOf<ReaderState>(ReaderState.Idle)
        private set
    var pendingKey by mutableStateOf<CardAccessKey?>(null)
        private set

    fun selectMode(m: ScanMode) {
        mode = m
        if (state is ReaderState.Error) state = ReaderState.Idle
    }

    fun setKey(key: CardAccessKey?) {
        pendingKey = key
    }

    fun reset() {
        state = ReaderState.Idle
    }

    /** Gọi từ luồng đọc NFC của Activity (không phải luồng chính). */
    fun onTag(tag: Tag) {
        viewModelScope.launch {
            state = ReaderState.Reading
            val result = withContext(Dispatchers.IO) {
                runCatching { readTag(tag) }
            }
            result.onSuccess { r ->
                if (r == null) {
                    state = ReaderState.Error("Không nhận ra thẻ. Thử giữ yên thẻ lâu hơn hoặc đổi chế độ đọc.")
                } else {
                    runCatching { history.save(r) }
                    state = ReaderState.Done(r)
                }
            }.onFailure { e ->
                state = ReaderState.Error(friendly(e))
            }
        }
    }

    private fun readTag(tag: Tag): ScanResult? {
        val techs = tag.techList.map { it.substringAfterLast('.') }
        return when (mode) {
            ScanMode.EMV -> readEmv(tag)
            ScanMode.TAG -> readTagGeneric(tag)
            ScanMode.IDCARD -> readIdCard(tag)
            ScanMode.AUTO -> {
                if (techs.contains("IsoDep")) {
                    val emv = runCatching { readEmv(tag) }.getOrNull()
                    if (emv?.emv?.pan != null) emv else readTagGeneric(tag)
                } else {
                    readTagGeneric(tag)
                }
            }
        }
    }

    private fun readEmv(tag: Tag): ScanResult {
        val isoDep = IsoDep.get(tag) ?: throw IllegalStateException("Thẻ không hỗ trợ ISO-DEP (không phải thẻ thanh toán).")
        val card: EmvCard = isoDep.use { EmvReader().read(it) }
        val last4 = Mask.last4(card.pan)
        return ScanResult(
            id = UUID.randomUUID().toString(),
            type = ScanType.EMV,
            timeMillis = System.currentTimeMillis(),
            titleScheme = card.scheme,
            subtitle = card.issuer?.bank,
            last4 = last4,
            emv = card
        )
    }

    private fun readIdCard(tag: Tag): ScanResult {
        val key = pendingKey
            ?: throw IllegalStateException("Chưa có khóa mở chip. Hãy quét MRZ mặt sau CCCD trước.")
        val isoDep = IsoDep.get(tag) ?: throw IllegalStateException("Thẻ không hỗ trợ ISO-DEP.")
        val data = isoDep.use { IdCardReader().read(it, key) }
        val last4 = Mask.last4(data.documentNumber)
        return ScanResult(
            id = UUID.randomUUID().toString(),
            type = ScanType.IDCARD,
            timeMillis = System.currentTimeMillis(),
            titleScheme = "CCCD",
            subtitle = data.fullName?.let { "••• (${data.protocol})" } ?: data.protocol,
            last4 = last4,
            idCard = data
        )
    }

    private fun readTagGeneric(tag: Tag): ScanResult {
        val data = NfcTagReader().read(tag)
        return ScanResult(
            id = UUID.randomUUID().toString(),
            type = ScanType.TAG,
            timeMillis = System.currentTimeMillis(),
            titleScheme = data.chipType.substringBefore(" ("),
            subtitle = if (data.ndefRecords.isNotEmpty()) "${data.ndefRecords.size} bản ghi NDEF" else null,
            last4 = null,
            tag = data
        )
    }

    private fun friendly(e: Throwable): String {
        val msg = e.message ?: e.javaClass.simpleName
        return when {
            msg.contains("Tag was lost", true) || msg.contains("lost", true) ->
                "Mất kết nối với thẻ. Giữ thẻ áp sát và yên hơn rồi thử lại."
            msg.contains("BAC", true) || msg.contains("authentication", true) || msg.contains("6300") ->
                "Sai khóa mở chip CCCD. Kiểm tra lại số thẻ, ngày sinh và ngày hết hạn từ MRZ."
            else -> msg
        }
    }
}
