package com.baophan.nfccheck.idcard

/** Khóa truy cập chip CCCD, lấy từ MRZ mặt sau. */
data class CardAccessKey(
    val documentNumber: String,
    val dateOfBirth: String,   // YYMMDD
    val dateOfExpiry: String   // YYMMDD
)

/**
 * Dữ liệu đọc được từ chip CCCD gắn chip.
 * Ảnh chân dung được lưu dạng byte JPEG đã giải mã (nếu giải mã được).
 */
data class IdCardData(
    val documentNumber: String?,
    val fullName: String?,
    val nationality: String?,
    val dateOfBirth: String?,
    val sex: String?,
    val dateOfExpiry: String?,
    val issuingState: String?,
    val personalNumber: String?,
    val extraText: String?,        // trích từ DG13 (quê quán, địa chỉ... ở dạng tốt nhất có thể)
    val faceJpeg: ByteArray?,      // ảnh chân dung đã giải mã sang JPEG, hoặc null
    val protocol: String,          // "PACE" hoặc "BAC"
    val dataGroupsRead: List<String>
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}
