package com.baophan.nfccheck.idcard

/**
 * Tách và phân tích MRZ (vùng đọc máy ở mặt sau CCCD) từ văn bản OCR.
 * CCCD Việt Nam dùng định dạng TD1: 3 dòng × 30 ký tự.
 *
 * Cách làm "dễ nhận" giống các app MRZ reader: không bắt đúng tuyệt đối từng ký tự,
 * chỉ cần tìm được các dòng trông giống MRZ rồi lấy ra số thẻ / ngày sinh / ngày hết hạn
 * theo đúng vị trí cố định của TD1 (không ép kiểm tra check digit, vì OCR hay sai 1-2 ký tự).
 */
object MrzUtil {

    /** Khóa truy cập lấy từ MRZ. */
    data class Parsed(val documentNumber: String, val dob: String, val expiry: String)

    private fun normalize(line: String): String =
        line.uppercase()
            .replace(" ", "")
            .replace('«', '<')
            .replace('»', '<')
            .replace('—', '<')
            .replace('–', '<')
            .replace('_', '<')
            .trim()

    /** Một dòng được coi là "giống MRZ" nếu đủ dài và phần lớn ký tự thuộc bộ A-Z0-9<. */
    private fun looksLikeMrz(line: String): Boolean {
        if (line.length < 25) return false
        val valid = line.count { it in 'A'..'Z' || it in '0'..'9' || it == '<' }
        return valid.toDouble() / line.length >= 0.8 && line.contains('<')
    }

    /** Trả về 3 dòng MRZ (mỗi dòng ép về đúng 30 ký tự) nếu nhận ra, ngược lại null. */
    fun extractLines(ocrText: String): List<String>? {
        val candidates = ocrText.lines()
            .map { normalize(it) }
            .filter { looksLikeMrz(it) }
        if (candidates.size < 3) return null
        // Lấy 3 dòng liên tiếp cuối cùng (MRZ nằm ở cuối mặt sau)
        val three = candidates.takeLast(3)
        return three.map { fit30(it) }
    }

    private fun fit30(s: String): String =
        if (s.length >= 30) s.substring(0, 30) else s.padEnd(30, '<')

    /** Phân tích 3 dòng TD1 ra số thẻ, ngày sinh, ngày hết hạn. */
    fun parse(lines: List<String>): Parsed? {
        if (lines.size < 2) return null
        val l1 = fit30(lines[0])
        val l2 = fit30(lines[1])

        // --- Số thẻ: TD1 vị trí [5..14), check digit ở [14], tràn sang optional data nếu dài ---
        val docField = l1.substring(5, 14)
        val check14 = l1[14]
        val documentNumber: String = if (check14 == '<') {
            val overflow = l1.substring(15).takeWhile { it != '<' } // phần còn lại + 1 check digit
            val extra = if (overflow.length >= 1) overflow.dropLast(1) else ""
            (docField + extra).replace("<", "")
        } else {
            docField.replace("<", "")
        }

        // --- Ngày sinh [0..6), ngày hết hạn [8..14) của dòng 2 ---
        val dob = l2.substring(0, 6)
        val expiry = l2.substring(8, 14)

        if (!dob.all { it.isDigit() } || !expiry.all { it.isDigit() }) return null
        if (documentNumber.length < 6) return null
        return Parsed(documentNumber, dob, expiry)
    }

    /** Gộp: từ text OCR ra thẳng khóa truy cập, hoặc null nếu chưa nhận đủ. */
    fun detect(ocrText: String): Parsed? {
        val lines = extractLines(ocrText) ?: return null
        return parse(lines)
    }
}
