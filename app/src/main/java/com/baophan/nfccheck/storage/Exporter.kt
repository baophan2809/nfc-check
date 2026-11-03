package com.baophan.nfccheck.storage

import com.baophan.nfccheck.model.ScanResult
import com.baophan.nfccheck.model.ScanType
import com.baophan.nfccheck.util.Mask
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Xuất kết quả ra chuỗi JSON hoặc TXT, có tùy chọn che số nhạy cảm. */
object Exporter {

    private fun fmt(time: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        return sdf.format(Date(time))
    }

    fun toJson(result: ScanResult, maskSensitive: Boolean): String {
        val o = ScanJson.toJson(result)
        o.put("timeLocal", fmt(result.timeMillis) + " (UTC+7)")
        if (maskSensitive) {
            o.optJSONObject("emv")?.let { e ->
                e.optString("pan").takeIf { it.isNotEmpty() && it != "null" }?.let { e.put("pan", Mask.maskPan(it)) }
            }
            o.optJSONObject("idCard")?.let { d ->
                d.optString("documentNumber").takeIf { it.isNotEmpty() && it != "null" }
                    ?.let { d.put("documentNumber", Mask.maskPan(it)) }
                d.put("faceJpeg", JSONObject.NULL)
            }
        }
        return o.toString(2)
    }

    fun toText(result: ScanResult, maskSensitive: Boolean): String {
        val sb = StringBuilder()
        sb.appendLine("===== NFC Check — kết quả quét =====")
        sb.appendLine("Thời gian: ${fmt(result.timeMillis)} (UTC+7)")
        sb.appendLine("Loại: ${result.titleScheme}")
        result.subtitle?.let { sb.appendLine("Ghi chú: $it") }
        sb.appendLine()

        when (result.type) {
            ScanType.EMV -> result.emv?.let { e ->
                val pan = e.pan
                sb.appendLine("Số thẻ: ${if (pan == null) "-" else if (maskSensitive) Mask.maskPan(pan) else pan}")
                sb.appendLine("Hết hạn: ${e.expiry ?: "-"}")
                sb.appendLine("Chủ thẻ: ${e.cardholderName ?: "-"}")
                sb.appendLine("Tổ chức thẻ: ${e.scheme}")
                e.issuer?.bank?.let { sb.appendLine("Ngân hàng: $it") }
                e.pinTryCounter?.let { sb.appendLine("Số lần nhập PIN còn lại: $it") }
                e.atc?.let { sb.appendLine("ATC: $it") }
                if (e.txLog.isNotEmpty()) {
                    sb.appendLine("\n-- Nhật ký giao dịch trên chip --")
                    e.txLog.forEach {
                        sb.appendLine("  ${it.date ?: "?"}  ${it.amount ?: "?"} ${it.currency ?: ""}  ${it.type ?: ""}")
                    }
                }
                sb.appendLine("\n-- Tag kỹ thuật --")
                e.tags.forEach {
                    sb.appendLine("  ${it.tag} ${it.name ?: ""}: ${it.valueHex}")
                }
                sb.appendLine("\n-- Log APDU --")
                e.apduLog.forEach {
                    sb.appendLine("  [${it.label}] -> ${it.sw}")
                    sb.appendLine("    C: ${it.command}")
                    sb.appendLine("    R: ${it.response}")
                }
            }
            ScanType.IDCARD -> result.idCard?.let { d ->
                val num = d.documentNumber
                sb.appendLine("Số CCCD: ${if (num == null) "-" else if (maskSensitive) Mask.maskPan(num) else num}")
                sb.appendLine("Họ tên: ${d.fullName ?: "-"}")
                sb.appendLine("Ngày sinh: ${d.dateOfBirth ?: "-"}")
                sb.appendLine("Giới tính: ${d.sex ?: "-"}")
                sb.appendLine("Quốc tịch: ${d.nationality ?: "-"}")
                sb.appendLine("Hết hạn: ${d.dateOfExpiry ?: "-"}")
                sb.appendLine("Giao thức: ${d.protocol}")
                d.extraText?.let { sb.appendLine("Thông tin thêm (DG13): $it") }
                sb.appendLine("Ảnh chân dung: ${if (d.faceJpeg != null) "có (không xuất ra text)" else "không đọc được"}")
            }
            ScanType.TAG -> result.tag?.let { t ->
                sb.appendLine("UID: ${t.uid}")
                sb.appendLine("Loại chip: ${t.chipType}")
                sb.appendLine("Công nghệ: ${t.techList.joinToString(", ")}")
                t.atqa?.let { sb.appendLine("ATQA: $it") }
                t.sak?.let { sb.appendLine("SAK: $it") }
                t.maxSize?.let { sb.appendLine("Dung lượng NDEF tối đa: $it byte") }
                t.ndefType?.let { sb.appendLine("Loại NDEF: $it") }
                if (t.ndefRecords.isNotEmpty()) {
                    sb.appendLine("\n-- Bản ghi NDEF --")
                    t.ndefRecords.forEach { sb.appendLine("  [${it.type}] ${it.payload}") }
                }
            }
        }
        return sb.toString()
    }
}
