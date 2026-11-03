package com.baophan.nfccheck.util

/** Tiện ích chuyển đổi hex <-> byte và vài phép định dạng dùng chung. */
object Hex {

    private val HEX = "0123456789ABCDEF".toCharArray()

    fun toHex(bytes: ByteArray?): String {
        if (bytes == null) return ""
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    fun toHexSpaced(bytes: ByteArray?): String {
        val raw = toHex(bytes)
        if (raw.isEmpty()) return ""
        return raw.chunked(2).joinToString(" ")
    }

    fun fromHex(s: String): ByteArray {
        val clean = s.filter { !it.isWhitespace() }
        require(clean.length % 2 == 0) { "Chuỗi hex phải có số ký tự chẵn" }
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            out[i / 2] = ((hexVal(clean[i]) shl 4) or hexVal(clean[i + 1])).toByte()
            i += 2
        }
        return out
    }

    private fun hexVal(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> throw IllegalArgumentException("Ký tự hex không hợp lệ: $c")
    }

    /** Diễn giải byte theo bảng mã ASCII, byte ngoài vùng in được thay bằng '.' */
    fun toAscii(bytes: ByteArray?): String {
        if (bytes == null) return ""
        val sb = StringBuilder()
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(if (v in 0x20..0x7E) v.toChar() else '.')
        }
        return sb.toString()
    }
}
