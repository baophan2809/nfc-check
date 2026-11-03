package com.baophan.nfccheck.idcard

/**
 * Tách chuỗi MRZ (vùng đọc máy, mặt sau CCCD) ra từ văn bản OCR.
 * CCCD/hộ chiếu dùng định dạng TD1 (3 dòng × 30 ký tự) hoặc TD3 (2 dòng × 44).
 */
object MrzUtil {

    private val MRZ_CHARS = Regex("^[A-Z0-9<]+$")

    /** Trả về chuỗi MRZ đã ghép (các dòng nối liền) nếu nhận ra, ngược lại null. */
    fun extract(ocrText: String): String? {
        val lines = ocrText
            .uppercase()
            .lines()
            .map { it.replace(" ", "").replace("«", "<").trim() }
            .filter { it.isNotEmpty() }

        // Gom các dòng trông giống MRZ (chỉ gồm A-Z 0-9 '<', đủ dài)
        val candidates = lines.filter { it.length in 28..46 && MRZ_CHARS.matches(it) && it.contains('<') }

        // TD1: 3 dòng dài ~30
        val td1 = candidates.filter { it.length in 28..32 }
        if (td1.size >= 3) {
            return td1.takeLast(3).joinToString("").take(90)
        }
        // TD3: 2 dòng dài ~44
        val td3 = candidates.filter { it.length in 42..46 }
        if (td3.size >= 2) {
            return td3.takeLast(2).joinToString("").take(88)
        }
        return null
    }

    /** Kiểm tra nhanh một chuỗi đã là MRZ hợp lệ về độ dài chưa. */
    fun isLikelyMrz(s: String): Boolean {
        val clean = s.uppercase().filter { !it.isWhitespace() }
        return (clean.length == 90 || clean.length == 88) && clean.contains('<')
    }
}
