package com.baophan.nfccheck.emv

/**
 * Tra nhà phát hành thẻ từ đầu số (BIN) — HOÀN TOÀN OFFLINE, không gọi mạng.
 * Dữ liệu là bảng tĩnh gói sẵn trong app: suy luận tổ chức thẻ theo dải IIN chuẩn,
 * và tra ngân hàng Việt Nam theo BIN NAPAS 9704xx khi nhận ra.
 */
object BinLookup {

    fun lookup(panOrToken: String?): IssuerInfo? {
        val pan = panOrToken?.filter { it.isDigit() } ?: return null
        if (pan.length < 6) return null
        val bin = pan.substring(0, 6)
        val scheme = detectScheme(pan)

        // Thẻ nội địa NAPAS: 9704 + 2 chữ số mã ngân hàng
        if (pan.startsWith("9704") && pan.length >= 6) {
            val bank = NAPAS_BANKS[pan.substring(0, 6)]
            return IssuerInfo(bin = bin, scheme = "NAPAS", bank = bank, country = "VN", type = null)
        }

        return IssuerInfo(bin = bin, scheme = scheme, bank = null, country = null, type = null)
    }

    fun detectScheme(pan: String): String {
        val p = pan.filter { it.isDigit() }
        if (p.isEmpty()) return "Không rõ"
        return when {
            p.startsWith("4") -> "Visa"
            p.length >= 4 && p.substring(0, 4).toIntOrNull()?.let { it in 2221..2720 } == true -> "Mastercard"
            p.length >= 2 && p.substring(0, 2).toIntOrNull()?.let { it in 51..55 } == true -> "Mastercard"
            p.startsWith("34") || p.startsWith("37") -> "American Express"
            p.length >= 4 && p.substring(0, 4).toIntOrNull()?.let { it in 3528..3589 } == true -> "JCB"
            p.startsWith("35") -> "JCB"
            p.startsWith("62") || p.startsWith("81") -> "UnionPay"
            p.startsWith("9704") -> "NAPAS"
            p.startsWith("36") || p.startsWith("38") || p.startsWith("30") -> "Diners Club"
            p.startsWith("65") || p.startsWith("6011") -> "Discover"
            else -> "Không rõ"
        }
    }

    /** BIN NAPAS 9704xx -> tên ngân hàng (danh sách phổ biến tại Việt Nam). */
    private val NAPAS_BANKS: Map<String, String> = mapOf(
        "970400" to "Saigonbank",
        "970403" to "Sacombank",
        "970405" to "Agribank",
        "970406" to "DongA Bank",
        "970407" to "Techcombank",
        "970408" to "GPBank",
        "970409" to "BacABank",
        "970410" to "Standard Chartered VN",
        "970412" to "PVcomBank",
        "970414" to "Oceanbank",
        "970415" to "VietinBank",
        "970416" to "ACB",
        "970418" to "BIDV",
        "970419" to "NCB",
        "970421" to "VRB",
        "970422" to "MB Bank",
        "970423" to "TPBank",
        "970424" to "Shinhan Bank",
        "970425" to "ABBANK",
        "970426" to "MSB",
        "970427" to "VietABank",
        "970428" to "NamABank",
        "970429" to "SCB",
        "970430" to "PGBank",
        "970431" to "Eximbank",
        "970432" to "VPBank",
        "970433" to "VietBank",
        "970434" to "Indovina Bank",
        "970436" to "Vietcombank",
        "970437" to "HDBank",
        "970438" to "Bao Viet Bank",
        "970439" to "Public Bank VN",
        "970440" to "SeABank",
        "970441" to "VIB",
        "970442" to "Hong Leong Bank VN",
        "970443" to "SHB",
        "970444" to "CBBank",
        "970446" to "COOPBANK",
        "970448" to "KBank",
        "970449" to "LPBank",
        "970452" to "KookminBank",
        "970454" to "CIMB VN",
        "970455" to "IBK HN",
        "970457" to "Woori Bank VN",
        "970458" to "UOB VN",
        "970462" to "Kiatnakin (KBank)",
        "970463" to "Mirae / DBS",
        "970464" to "DBS VN"
    )
}
