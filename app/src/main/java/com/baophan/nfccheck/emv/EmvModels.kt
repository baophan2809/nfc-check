package com.baophan.nfccheck.emv

/** Một cặp lệnh/đáp ứng APDU để hiển thị ở tab LOG. */
data class ApduExchange(
    val label: String,
    val command: String,   // hex
    val response: String,  // hex
    val sw: String         // status word, ví dụ "9000"
)

/** Một dòng nhật ký giao dịch lưu trên chip (nếu thẻ có hỗ trợ). */
data class TxLogEntry(
    val amount: String?,
    val currency: String?,
    val date: String?,
    val type: String?,
    val raw: String
)

/** Một tag EMV đã giải mã để hiển thị ở tab kỹ thuật. */
data class EmvTagView(
    val tag: String,
    val name: String?,
    val valueHex: String,
    val valueAscii: String?
)

/** Ứng dụng (AID) đọc được trên thẻ. */
data class EmvApplication(
    val aid: String,
    val label: String?,
    val preferredName: String?,
    val scheme: String
)

/**
 * Kết quả đọc một thẻ thanh toán EMV.
 * Lưu ý: chip KHÔNG trả về CVV/CVV2 hay mã PIN — các trường đó không tồn tại ở đây.
 */
data class EmvCard(
    val pan: String?,              // số thẻ đầy đủ (hoặc token với thẻ trong ví)
    val expiry: String?,           // MM/YY
    val effectiveDate: String?,    // MM/YY
    val cardholderName: String?,
    val scheme: String,            // Visa / Mastercard / JCB / UnionPay / Napas ...
    val applications: List<EmvApplication>,
    val selectedAid: String?,
    val pinTryCounter: Int?,       // 9F17
    val atc: Int?,                 // 9F36
    val lastOnlineAtc: Int?,       // 9F13
    val issuerCountry: String?,
    val panSequence: String?,
    val issuer: IssuerInfo?,       // tra từ BIN (offline)
    val txLog: List<TxLogEntry>,
    val tags: List<EmvTagView>,
    val apduLog: List<ApduExchange>
)

/** Thông tin nhà phát hành tra offline từ đầu số thẻ (BIN). */
data class IssuerInfo(
    val bin: String,
    val scheme: String?,
    val bank: String?,
    val country: String?,
    val type: String?             // ví dụ Credit / Debit (nếu suy luận được)
)
