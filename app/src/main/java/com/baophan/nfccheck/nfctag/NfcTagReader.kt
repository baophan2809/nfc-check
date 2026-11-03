package com.baophan.nfccheck.nfctag

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import com.baophan.nfccheck.util.Hex

/**
 * Đọc thông tin cơ bản của thẻ/tag NFC không phải thẻ thanh toán:
 * UID, danh sách công nghệ, loại chip, và nội dung NDEF (link/text) nếu có.
 * Chỉ đọc — không ghi, không thử bẻ khóa sector.
 */
class NfcTagReader {

    fun read(tag: Tag): NfcTagData {
        val uid = Hex.toHexSpaced(tag.id)
        val techList = tag.techList.map { it.substringAfterLast('.') }

        var atqa: String? = null
        var sak: String? = null
        NfcA.get(tag)?.let {
            atqa = Hex.toHexSpaced(it.atqa)
            sak = "%02X".format(it.sak.toInt() and 0xFF)
        }

        val chipType = detectChip(tag, techList)

        var maxSize: Int? = null
        var writable: Boolean? = null
        var ndefType: String? = null
        val records = ArrayList<NdefRecordView>()

        Ndef.get(tag)?.let { ndef ->
            maxSize = ndef.maxSize
            writable = ndef.isWritable
            ndefType = ndef.type
            try {
                ndef.connect()
                val msg = ndef.cachedNdefMessage ?: ndef.ndefMessage
                msg?.records?.forEach { r ->
                    records.add(parseRecord(r))
                }
            } catch (_: Exception) {
            } finally {
                try { ndef.close() } catch (_: Exception) {}
            }
        }

        return NfcTagData(
            uid = uid,
            techList = techList,
            chipType = chipType,
            atqa = atqa,
            sak = sak,
            maxSize = maxSize,
            writable = writable,
            ndefType = ndefType,
            ndefRecords = records
        )
    }

    private fun detectChip(tag: Tag, tech: List<String>): String {
        MifareClassic.get(tag)?.let {
            val kb = it.size / 1024
            return "MIFARE Classic ${kb}K (${it.sectorCount} sector)"
        }
        MifareUltralight.get(tag)?.let {
            return when (it.type) {
                MifareUltralight.TYPE_ULTRALIGHT -> "MIFARE Ultralight"
                MifareUltralight.TYPE_ULTRALIGHT_C -> "MIFARE Ultralight C"
                else -> "MIFARE Ultralight (NTAG)"
            }
        }
        return when {
            tech.contains("IsoDep") && tech.contains("NfcA") -> "ISO 14443-4 Type A (có thể DESFire/eID)"
            tech.contains("IsoDep") && tech.contains("NfcB") -> "ISO 14443-4 Type B"
            tech.contains("NfcV") -> "ISO 15693 (Vicinity)"
            tech.contains("NfcF") -> "FeliCa"
            tech.contains("NfcA") -> "ISO 14443 Type A"
            tech.contains("NfcB") -> "ISO 14443 Type B"
            else -> tech.joinToString(", ")
        }
    }

    private fun parseRecord(r: android.nfc.NdefRecord): NdefRecordView {
        val tnf = r.tnf
        val typeStr = String(r.type, Charsets.US_ASCII)
        // Bản ghi Text theo chuẩn NFC Forum
        if (tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN && typeStr == "T") {
            val payload = r.payload
            if (payload.isNotEmpty()) {
                val status = payload[0].toInt() and 0xFF
                val langLen = status and 0x3F
                val text = String(payload, 1 + langLen, payload.size - 1 - langLen, Charsets.UTF_8)
                return NdefRecordView("Text", text)
            }
        }
        // Bản ghi URI
        if (tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN && typeStr == "U") {
            val payload = r.payload
            if (payload.isNotEmpty()) {
                val prefix = URI_PREFIX.getOrElse(payload[0].toInt() and 0xFF) { "" }
                val uri = prefix + String(payload, 1, payload.size - 1, Charsets.UTF_8)
                return NdefRecordView("URI", uri)
            }
        }
        val type = when (tnf) {
            android.nfc.NdefRecord.TNF_MIME_MEDIA -> "MIME: $typeStr"
            android.nfc.NdefRecord.TNF_ABSOLUTE_URI -> "URI tuyệt đối"
            android.nfc.NdefRecord.TNF_EXTERNAL_TYPE -> "External: $typeStr"
            else -> "TNF $tnf / $typeStr"
        }
        return NdefRecordView(type, Hex.toAscii(r.payload))
    }

    companion object {
        private val URI_PREFIX = listOf(
            "", "http://www.", "https://www.", "http://", "https://",
            "tel:", "mailto:", "ftp://anonymous:anonymous@", "ftp://ftp.",
            "ftps://", "sftp://", "smb://", "nfs://", "ftp://", "dav://",
            "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:",
            "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://",
            "tcpobex://", "irdaobex://", "file://", "urn:epc:id:",
            "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:"
        )
    }
}
