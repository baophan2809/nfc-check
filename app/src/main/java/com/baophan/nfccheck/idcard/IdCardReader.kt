package com.baophan.nfccheck.idcard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.nfc.tech.IsoDep
import net.sf.scuba.smartcards.CardService
import org.jmrtd.BACKey
import org.jmrtd.PACEKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import java.io.ByteArrayOutputStream

/**
 * Đọc chip CCCD gắn chip theo chuẩn ICAO 9303 (eMRTD), dùng thư viện JMRTD.
 * Cần khóa truy cập từ MRZ (số thẻ + ngày sinh + ngày hết hạn) mới mở được chip.
 * Chỉ đọc các nhóm dữ liệu công khai: DG1 (MRZ), DG2 (ảnh), DG13 (thông tin cá nhân VN).
 */
class IdCardReader {

    fun read(isoDep: IsoDep, key: CardAccessKey): IdCardData {
        if (!isoDep.isConnected) isoDep.connect()
        try { isoDep.timeout = 10000 } catch (_: Exception) {}
        val cardService = CardService.getInstance(isoDep)
        cardService.open()

        val service = PassportService(
            cardService,
            PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
            PassportService.DEFAULT_MAX_BLOCKSIZE,
            false,
            false
        )
        service.open()

        val bacKey = BACKey(key.documentNumber, key.dateOfBirth, key.dateOfExpiry)
        var protocol = "BAC"
        var paceSucceeded = false

        // Ưu tiên PACE nếu chip công bố hỗ trợ (EF.CardAccess)
        try {
            val cae = CardAccessFile(service.getInputStream(PassportService.EF_CARD_ACCESS))
            for (si in cae.securityInfos) {
                if (si is PACEInfo) {
                    val paceKey = PACEKeySpec.createMRZKey(bacKey)
                    service.doPACE(
                        paceKey,
                        si.objectIdentifier,
                        PACEInfo.toParameterSpec(si.parameterId),
                        null
                    )
                    paceSucceeded = true
                    protocol = "PACE"
                    break
                }
            }
        } catch (_: Exception) {
            paceSucceeded = false
        }

        service.sendSelectApplet(paceSucceeded)

        if (!paceSucceeded) {
            service.doBAC(bacKey)
            protocol = "BAC"
        }

        val dgRead = ArrayList<String>()

        // DG1 — MRZ (thông tin có cấu trúc)
        var docNumber: String? = null
        var fullName: String? = null
        var nationality: String? = null
        var dob: String? = null
        var sex: String? = null
        var expiry: String? = null
        var issuing: String? = null
        var personalNumber: String? = null
        try {
            val dg1 = DG1File(service.getInputStream(PassportService.EF_DG1))
            val mrz = dg1.mrzInfo
            docNumber = mrz.documentNumber?.trim('<', ' ')
            fullName = listOf(mrz.secondaryIdentifier, mrz.primaryIdentifier)
                .joinToString(" ") { it.replace("<", " ").trim() }
                .trim()
            nationality = mrz.nationality
            dob = mrz.dateOfBirth
            sex = mrz.gender?.toString()
            expiry = mrz.dateOfExpiry
            issuing = mrz.issuingState
            personalNumber = mrz.optionalData1?.replace("<", "")?.trim()?.ifBlank { null }
            dgRead.add("DG1")
        } catch (_: Exception) {
        }

        // DG13 — thông tin cá nhân riêng của CCCD Việt Nam (họ tên đầy đủ, quê quán...)
        var extraText: String? = null
        try {
            val raw = service.getInputStream(PassportService.EF_DG13).readBytes()
            if (raw.isNotEmpty()) {
                extraText = extractReadable(raw)
                dgRead.add("DG13")
            }
        } catch (_: Exception) {
        }

        // DG2 — ảnh chân dung
        var faceJpeg: ByteArray? = null
        try {
            val dg2 = DG2File(service.getInputStream(PassportService.EF_DG2))
            val faceInfos = dg2.faceInfos
            outer@ for (fi in faceInfos) {
                for (img in fi.faceImageInfos) {
                    val bytes = img.imageInputStream.readBytes()
                    val mime = (img.mimeType ?: "").lowercase()
                    faceJpeg = decodeToJpeg(bytes, mime)
                    if (faceJpeg != null) break@outer
                }
            }
            if (faceJpeg != null) dgRead.add("DG2")
        } catch (_: Exception) {
        }

        try { service.close() } catch (_: Exception) {}

        return IdCardData(
            documentNumber = docNumber,
            fullName = fullName?.ifBlank { null },
            nationality = nationality,
            dateOfBirth = formatYymmdd(dob),
            sex = mapSex(sex),
            dateOfExpiry = formatYymmdd(expiry),
            issuingState = issuing,
            personalNumber = personalNumber,
            extraText = extraText,
            faceJpeg = faceJpeg,
            protocol = protocol,
            dataGroupsRead = dgRead
        )
    }

    private fun decodeToJpeg(bytes: ByteArray, mime: String): ByteArray? {
        return try {
            val bitmap: Bitmap? = if (mime.contains("jp2") || mime.contains("jpeg2000") || mime.contains("jpx")) {
                decodeJp2(bytes)
            } else {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            if (bitmap == null) return null
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.toByteArray()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Giải mã ảnh JPEG2000 bằng thư viện JP2 for Android, gọi qua reflection để
     * không phụ thuộc vào tên package cụ thể (com.gemalto.jp2 hoặc dev.keiji.jp2).
     */
    private fun decodeJp2(bytes: ByteArray): Bitmap? {
        for (cls in listOf("com.gemalto.jp2.JP2Decoder", "dev.keiji.jp2.JP2Decoder")) {
            try {
                val clazz = Class.forName(cls)
                val instance = clazz.getConstructor(ByteArray::class.java).newInstance(bytes)
                val result = clazz.getMethod("decode").invoke(instance)
                if (result is Bitmap) return result
            } catch (_: Throwable) {
            }
        }
        return null
    }

    /** Trích các đoạn văn bản đọc được từ DG13 (định dạng riêng, cố gắng tốt nhất). */
    private fun extractReadable(raw: ByteArray): String? {
        val decoded = String(raw, Charsets.UTF_8)
        val segments = Regex("[\\p{L}\\p{N}/.,\\- ]{2,}")
            .findAll(decoded)
            .map { it.value.trim() }
            .filter { it.length >= 2 && it.any { c -> c.isLetter() } }
            .distinct()
            .toList()
        val joined = segments.joinToString(" | ")
        return joined.ifBlank { null }
    }

    private fun formatYymmdd(s: String?): String? {
        if (s == null || s.length < 6) return s
        val yy = s.substring(0, 2)
        val mm = s.substring(2, 4)
        val dd = s.substring(4, 6)
        return "$dd/$mm/$yy"
    }

    private fun mapSex(s: String?): String? = when (s?.uppercase()) {
        "MALE", "M" -> "Nam"
        "FEMALE", "F" -> "Nữ"
        null -> null
        else -> s
    }
}
