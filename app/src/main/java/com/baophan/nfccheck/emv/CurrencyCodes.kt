package com.baophan.nfccheck.emv

/** Mã tiền tệ ISO 4217 (dạng số) -> tên ngắn, dùng cho nhật ký giao dịch. */
object CurrencyCodes {
    private val MAP = mapOf(
        "0704" to "VND",
        "0840" to "USD",
        "0978" to "EUR",
        "0826" to "GBP",
        "0392" to "JPY",
        "0156" to "CNY",
        "0410" to "KRW",
        "0702" to "SGD",
        "0764" to "THB",
        "0036" to "AUD",
        "0124" to "CAD",
        "0344" to "HKD",
        "0901" to "TWD",
        "0458" to "MYR",
        "0360" to "IDR",
        "0608" to "PHP",
        "0356" to "INR"
    )

    fun name(numeric: String): String {
        val key = numeric.padStart(4, '0').takeLast(4)
        return MAP[key] ?: "ISO $key"
    }
}
