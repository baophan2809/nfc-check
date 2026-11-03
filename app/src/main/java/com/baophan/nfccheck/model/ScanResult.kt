package com.baophan.nfccheck.model

import com.baophan.nfccheck.emv.EmvCard
import com.baophan.nfccheck.idcard.IdCardData
import com.baophan.nfccheck.nfctag.NfcTagData

enum class ScanType { EMV, IDCARD, TAG }

/** Kết quả quét thống nhất cho cả ba loại thẻ, dùng chung cho UI, lịch sử và xuất file. */
data class ScanResult(
    val id: String,
    val type: ScanType,
    val timeMillis: Long,
    val titleScheme: String,    // nhãn an toàn cho danh sách (Visa / CCCD / MIFARE...)
    val subtitle: String?,      // ngân hàng... (không chứa số thẻ)
    val last4: String?,         // 4 số cuối thật — chỉ nằm trong khối đã mã hóa
    val emv: EmvCard? = null,
    val idCard: IdCardData? = null,
    val tag: NfcTagData? = null
)
