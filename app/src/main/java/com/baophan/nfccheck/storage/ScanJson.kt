package com.baophan.nfccheck.storage

import android.util.Base64
import com.baophan.nfccheck.emv.ApduExchange
import com.baophan.nfccheck.emv.EmvApplication
import com.baophan.nfccheck.emv.EmvCard
import com.baophan.nfccheck.emv.EmvTagView
import com.baophan.nfccheck.emv.IssuerInfo
import com.baophan.nfccheck.emv.TxLogEntry
import com.baophan.nfccheck.idcard.IdCardData
import com.baophan.nfccheck.model.ScanResult
import com.baophan.nfccheck.model.ScanType
import com.baophan.nfccheck.nfctag.NdefRecordView
import com.baophan.nfccheck.nfctag.NfcTagData
import org.json.JSONArray
import org.json.JSONObject

/** Chuyển ScanResult <-> JSON để lưu lịch sử (mã hóa) và xuất file. */
object ScanJson {

    fun toJson(r: ScanResult): JSONObject {
        val o = JSONObject()
        o.put("id", r.id)
        o.put("type", r.type.name)
        o.put("time", r.timeMillis)
        o.put("titleScheme", r.titleScheme)
        o.put("subtitle", r.subtitle ?: JSONObject.NULL)
        o.put("last4", r.last4 ?: JSONObject.NULL)
        r.emv?.let { o.put("emv", emvToJson(it)) }
        r.idCard?.let { o.put("idCard", idToJson(it)) }
        r.tag?.let { o.put("tag", tagToJson(it)) }
        return o
    }

    fun fromJson(o: JSONObject): ScanResult = ScanResult(
        id = o.getString("id"),
        type = ScanType.valueOf(o.getString("type")),
        timeMillis = o.getLong("time"),
        titleScheme = o.getString("titleScheme"),
        subtitle = o.optStringOrNull("subtitle"),
        last4 = o.optStringOrNull("last4"),
        emv = o.optJSONObject("emv")?.let { emvFromJson(it) },
        idCard = o.optJSONObject("idCard")?.let { idFromJson(it) },
        tag = o.optJSONObject("tag")?.let { tagFromJson(it) }
    )

    // ---- EMV ----

    private fun emvToJson(e: EmvCard): JSONObject = JSONObject().apply {
        put("pan", e.pan ?: JSONObject.NULL)
        put("expiry", e.expiry ?: JSONObject.NULL)
        put("effectiveDate", e.effectiveDate ?: JSONObject.NULL)
        put("cardholderName", e.cardholderName ?: JSONObject.NULL)
        put("scheme", e.scheme)
        put("selectedAid", e.selectedAid ?: JSONObject.NULL)
        put("pinTryCounter", e.pinTryCounter ?: JSONObject.NULL)
        put("atc", e.atc ?: JSONObject.NULL)
        put("lastOnlineAtc", e.lastOnlineAtc ?: JSONObject.NULL)
        put("issuerCountry", e.issuerCountry ?: JSONObject.NULL)
        put("panSequence", e.panSequence ?: JSONObject.NULL)
        e.issuer?.let { iss ->
            put("issuer", JSONObject().apply {
                put("bin", iss.bin)
                put("scheme", iss.scheme ?: JSONObject.NULL)
                put("bank", iss.bank ?: JSONObject.NULL)
                put("country", iss.country ?: JSONObject.NULL)
                put("type", iss.type ?: JSONObject.NULL)
            })
        }
        put("applications", JSONArray().apply {
            e.applications.forEach {
                put(JSONObject().apply {
                    put("aid", it.aid)
                    put("label", it.label ?: JSONObject.NULL)
                    put("preferredName", it.preferredName ?: JSONObject.NULL)
                    put("scheme", it.scheme)
                })
            }
        })
        put("txLog", JSONArray().apply {
            e.txLog.forEach {
                put(JSONObject().apply {
                    put("amount", it.amount ?: JSONObject.NULL)
                    put("currency", it.currency ?: JSONObject.NULL)
                    put("date", it.date ?: JSONObject.NULL)
                    put("type", it.type ?: JSONObject.NULL)
                    put("raw", it.raw)
                })
            }
        })
        put("tags", JSONArray().apply {
            e.tags.forEach {
                put(JSONObject().apply {
                    put("tag", it.tag)
                    put("name", it.name ?: JSONObject.NULL)
                    put("valueHex", it.valueHex)
                    put("valueAscii", it.valueAscii ?: JSONObject.NULL)
                })
            }
        })
        put("apduLog", JSONArray().apply {
            e.apduLog.forEach {
                put(JSONObject().apply {
                    put("label", it.label)
                    put("command", it.command)
                    put("response", it.response)
                    put("sw", it.sw)
                })
            }
        })
    }

    private fun emvFromJson(o: JSONObject): EmvCard = EmvCard(
        pan = o.optStringOrNull("pan"),
        expiry = o.optStringOrNull("expiry"),
        effectiveDate = o.optStringOrNull("effectiveDate"),
        cardholderName = o.optStringOrNull("cardholderName"),
        scheme = o.optString("scheme", "Không rõ"),
        applications = o.optJSONArray("applications").mapObj {
            EmvApplication(
                it.getString("aid"),
                it.optStringOrNull("label"),
                it.optStringOrNull("preferredName"),
                it.optString("scheme", "Không rõ")
            )
        },
        selectedAid = o.optStringOrNull("selectedAid"),
        pinTryCounter = o.optIntOrNull("pinTryCounter"),
        atc = o.optIntOrNull("atc"),
        lastOnlineAtc = o.optIntOrNull("lastOnlineAtc"),
        issuerCountry = o.optStringOrNull("issuerCountry"),
        panSequence = o.optStringOrNull("panSequence"),
        issuer = o.optJSONObject("issuer")?.let {
            IssuerInfo(
                it.getString("bin"),
                it.optStringOrNull("scheme"),
                it.optStringOrNull("bank"),
                it.optStringOrNull("country"),
                it.optStringOrNull("type")
            )
        },
        txLog = o.optJSONArray("txLog").mapObj {
            TxLogEntry(
                it.optStringOrNull("amount"),
                it.optStringOrNull("currency"),
                it.optStringOrNull("date"),
                it.optStringOrNull("type"),
                it.optString("raw", "")
            )
        },
        tags = o.optJSONArray("tags").mapObj {
            EmvTagView(
                it.getString("tag"),
                it.optStringOrNull("name"),
                it.optString("valueHex", ""),
                it.optStringOrNull("valueAscii")
            )
        },
        apduLog = o.optJSONArray("apduLog").mapObj {
            ApduExchange(
                it.optString("label", ""),
                it.optString("command", ""),
                it.optString("response", ""),
                it.optString("sw", "")
            )
        }
    )

    // ---- ID card ----

    private fun idToJson(d: IdCardData): JSONObject = JSONObject().apply {
        put("documentNumber", d.documentNumber ?: JSONObject.NULL)
        put("fullName", d.fullName ?: JSONObject.NULL)
        put("nationality", d.nationality ?: JSONObject.NULL)
        put("dateOfBirth", d.dateOfBirth ?: JSONObject.NULL)
        put("sex", d.sex ?: JSONObject.NULL)
        put("dateOfExpiry", d.dateOfExpiry ?: JSONObject.NULL)
        put("issuingState", d.issuingState ?: JSONObject.NULL)
        put("personalNumber", d.personalNumber ?: JSONObject.NULL)
        put("extraText", d.extraText ?: JSONObject.NULL)
        put("protocol", d.protocol)
        put("dataGroupsRead", JSONArray(d.dataGroupsRead))
        put("faceJpeg", d.faceJpeg?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
    }

    private fun idFromJson(o: JSONObject): IdCardData = IdCardData(
        documentNumber = o.optStringOrNull("documentNumber"),
        fullName = o.optStringOrNull("fullName"),
        nationality = o.optStringOrNull("nationality"),
        dateOfBirth = o.optStringOrNull("dateOfBirth"),
        sex = o.optStringOrNull("sex"),
        dateOfExpiry = o.optStringOrNull("dateOfExpiry"),
        issuingState = o.optStringOrNull("issuingState"),
        personalNumber = o.optStringOrNull("personalNumber"),
        extraText = o.optStringOrNull("extraText"),
        faceJpeg = o.optStringOrNull("faceJpeg")?.let { Base64.decode(it, Base64.NO_WRAP) },
        protocol = o.optString("protocol", "BAC"),
        dataGroupsRead = o.optJSONArray("dataGroupsRead").mapStr()
    )

    // ---- NFC tag ----

    private fun tagToJson(t: NfcTagData): JSONObject = JSONObject().apply {
        put("uid", t.uid)
        put("techList", JSONArray(t.techList))
        put("chipType", t.chipType)
        put("atqa", t.atqa ?: JSONObject.NULL)
        put("sak", t.sak ?: JSONObject.NULL)
        put("maxSize", t.maxSize ?: JSONObject.NULL)
        put("writable", t.writable ?: JSONObject.NULL)
        put("ndefType", t.ndefType ?: JSONObject.NULL)
        put("ndefRecords", JSONArray().apply {
            t.ndefRecords.forEach {
                put(JSONObject().apply {
                    put("type", it.type)
                    put("payload", it.payload)
                })
            }
        })
    }

    private fun tagFromJson(o: JSONObject): NfcTagData = NfcTagData(
        uid = o.optString("uid", ""),
        techList = o.optJSONArray("techList").mapStr(),
        chipType = o.optString("chipType", ""),
        atqa = o.optStringOrNull("atqa"),
        sak = o.optStringOrNull("sak"),
        maxSize = o.optIntOrNull("maxSize"),
        writable = if (o.isNull("writable")) null else o.optBoolean("writable"),
        ndefType = o.optStringOrNull("ndefType"),
        ndefRecords = o.optJSONArray("ndefRecords").mapObj {
            NdefRecordView(it.optString("type", ""), it.optString("payload", ""))
        }
    )

    // ---- Helper mở rộng ----

    private fun JSONObject.optStringOrNull(k: String): String? =
        if (isNull(k)) null else optString(k, null).takeIf { !it.isNullOrEmpty() }

    private fun JSONObject.optIntOrNull(k: String): Int? =
        if (isNull(k) || !has(k)) null else optInt(k)

    private fun <T> JSONArray?.mapObj(f: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (i in 0 until length()) out.add(f(getJSONObject(i)))
        return out
    }

    private fun JSONArray?.mapStr(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(length())
        for (i in 0 until length()) out.add(getString(i))
        return out
    }
}
