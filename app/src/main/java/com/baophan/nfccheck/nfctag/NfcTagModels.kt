package com.baophan.nfccheck.nfctag

data class NdefRecordView(
    val type: String,
    val payload: String
)

data class NfcTagData(
    val uid: String,
    val techList: List<String>,
    val chipType: String,
    val atqa: String?,
    val sak: String?,
    val maxSize: Int?,
    val writable: Boolean?,
    val ndefType: String?,
    val ndefRecords: List<NdefRecordView>
)
