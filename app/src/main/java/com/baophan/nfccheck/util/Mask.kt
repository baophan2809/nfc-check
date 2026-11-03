package com.baophan.nfccheck.util

object Mask {

    /** Che toàn bộ chữ số, giữ cách nhóm 4 số cho dễ nhìn. Ví dụ: •••• •••• •••• ••••  */
    fun maskAll(number: String): String {
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return "••••"
        return digits.map { '•' }.chunkedJoin()
    }

    /** Che giữa, giữ 6 số đầu + 4 số cuối (dùng khi xuất file có che). */
    fun maskPan(number: String): String {
        val d = number.filter { it.isDigit() }
        if (d.length <= 10) return maskAll(d)
        val head = d.substring(0, 6)
        val tail = d.takeLast(4)
        val middle = "•".repeat(d.length - 10)
        return (head + middle + tail).chunked(4).joinToString(" ")
    }

    fun last4(number: String?): String? {
        val d = number?.filter { it.isDigit() } ?: return null
        return if (d.length >= 4) d.takeLast(4) else null
    }

    /** Nhóm 4 ký tự cách nhau bằng khoảng trắng. */
    fun format(number: String): String = number.filter { it.isDigit() }.chunked(4).joinToString(" ")

    private fun String.chunkedJoin(): String = this.chunked(4).joinToString(" ")
    private fun List<Char>.chunkedJoin(): String = this.joinToString("").chunked(4).joinToString(" ")
}
