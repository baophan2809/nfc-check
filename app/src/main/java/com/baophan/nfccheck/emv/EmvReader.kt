package com.baophan.nfccheck.emv

import android.nfc.tech.IsoDep
import com.baophan.nfccheck.util.Hex
import java.io.ByteArrayOutputStream

/**
 * Đọc dữ liệu CÔNG KHAI của thẻ thanh toán EMV không tiếp xúc.
 *
 * App chỉ gửi các lệnh đọc tiêu chuẩn (SELECT, GPO, READ RECORD, GET DATA) để
 * lấy thông tin mà chip tự nguyện trả về. Không có lệnh trừ tiền, không ghi thẻ,
 * không tạo giao dịch. Chip không bao giờ trả về CVV2 hay mã PIN.
 */
class EmvReader {

    private val log = ArrayList<ApduExchange>()

    fun read(isoDep: IsoDep): EmvCard {
        log.clear()
        if (!isoDep.isConnected) isoDep.connect()
        try { isoDep.timeout = 5000 } catch (_: Exception) {}

        val allTlv = ArrayList<TlvNode>()
        val apps = ArrayList<EmvApplication>()

        // 1) SELECT PPSE để liệt kê các ứng dụng không tiếp xúc
        val ppse = selectByName(isoDep, PPSE.toByteArray(Charsets.US_ASCII), "SELECT PPSE")
        if (ppse != null) {
            allTlv.addAll(ppse)
            apps.addAll(extractApplications(ppse))
        }

        // Nếu PPSE không ra AID nào, thử vài AID phổ biến
        if (apps.isEmpty()) {
            for ((aidHex, scheme) in WELL_KNOWN_AIDS) {
                val fci = selectAid(isoDep, Hex.fromHex(aidHex), "SELECT AID (thử) $scheme")
                if (fci != null) {
                    apps.add(EmvApplication(aidHex, labelOf(fci), prefNameOf(fci), scheme))
                }
            }
        }

        // 2) Chọn ứng dụng ưu tiên cao nhất rồi GPO + đọc record
        var selectedAid: String? = null
        val recordTlv = ArrayList<TlvNode>()
        val chosen = apps.minByOrNull { priorityOf(it.aid) }
        if (chosen != null) {
            selectedAid = chosen.aid
            val fci = selectAid(isoDep, Hex.fromHex(chosen.aid), "SELECT AID ${chosen.scheme}")
            if (fci != null) {
                recordTlv.addAll(fci)
                val pdol = Tlv.findFirst(fci, "9F38")?.value
                val gpo = runGpo(isoDep, pdol)
                if (gpo != null) {
                    recordTlv.addAll(gpo)
                    val afl = Tlv.findFirst(gpo, "94")?.value
                        ?: Tlv.findFirst(gpo, "80")?.value?.let { if (it.size > 2) it.copyOfRange(2, it.size) else null }
                    if (afl != null) {
                        recordTlv.addAll(readRecordsFromAfl(isoDep, afl))
                    }
                }
            }
        }
        allTlv.addAll(recordTlv)

        // 3) GET DATA cho vài giá trị hữu ích (nếu thẻ cho phép)
        val pinTry = getDataInt(isoDep, "9F17", "GET DATA PIN Try Counter")
        val atc = getDataInt(isoDep, "9F36", "GET DATA ATC")
        val lastAtc = getDataInt(isoDep, "9F13", "GET DATA Last Online ATC")

        // 4) Nhật ký giao dịch trên chip (nếu có)
        val txLog = readTransactionLog(isoDep, recordTlv)

        // Tổng hợp kết quả
        val pan = Tlv.findFirst(allTlv, "5A")?.value?.let { decodePan(it) }
            ?: Tlv.findFirst(allTlv, "57")?.value?.let { track2Pan(it) }
        val expiry = Tlv.findFirst(allTlv, "5F24")?.value?.let { yymmddToMonthYear(it) }
            ?: Tlv.findFirst(allTlv, "57")?.value?.let { track2Expiry(it) }
        val effective = Tlv.findFirst(allTlv, "5F25")?.value?.let { yymmddToMonthYear(it) }
        val name = Tlv.findFirst(allTlv, "5F20")?.value?.let { String(it, Charsets.US_ASCII).trim() }
        val country = Tlv.findFirst(allTlv, "5F28")?.value?.let { Hex.toHex(it) }
        val panSeq = Tlv.findFirst(allTlv, "5F34")?.value?.let { Hex.toHex(it) }

        val scheme = chosen?.scheme ?: pan?.let { BinLookup.detectScheme(it) } ?: "Không rõ"
        val issuer = BinLookup.lookup(pan)

        val tagViews = buildTagViews(allTlv)

        return EmvCard(
            pan = pan,
            expiry = expiry,
            effectiveDate = effective,
            cardholderName = if (name.isNullOrBlank()) null else name,
            scheme = scheme,
            applications = apps,
            selectedAid = selectedAid,
            pinTryCounter = pinTry,
            atc = atc,
            lastOnlineAtc = lastAtc,
            issuerCountry = country,
            panSequence = panSeq,
            issuer = issuer,
            txLog = txLog,
            tags = tagViews,
            apduLog = ArrayList(log)
        )
    }

    // ---- Các bước APDU ----

    private fun selectByName(isoDep: IsoDep, name: ByteArray, label: String): List<TlvNode>? {
        val cmd = buildApdu(0x00, 0xA4, 0x04, 0x00, name, 0x00)
        val resp = transceive(isoDep, label, cmd)
        return if (resp.ok) Tlv.parse(resp.data) else null
    }

    private fun selectAid(isoDep: IsoDep, aid: ByteArray, label: String): List<TlvNode>? {
        val cmd = buildApdu(0x00, 0xA4, 0x04, 0x00, aid, 0x00)
        val resp = transceive(isoDep, label, cmd)
        return if (resp.ok) Tlv.parse(resp.data) else null
    }

    private fun runGpo(isoDep: IsoDep, pdol: ByteArray?): List<TlvNode>? {
        val pdolData = if (pdol == null || pdol.isEmpty()) ByteArray(0) else buildDolValues(pdol)
        // Dữ liệu lệnh GPO được bọc trong tag 83
        val wrapped = ByteArrayOutputStream()
        wrapped.write(0x83)
        if (pdolData.size < 0x80) {
            wrapped.write(pdolData.size)
        } else {
            wrapped.write(0x81); wrapped.write(pdolData.size)
        }
        wrapped.write(pdolData)
        val cmd = buildApdu(0x80, 0xA8, 0x00, 0x00, wrapped.toByteArray(), 0x00)
        val resp = transceive(isoDep, "GET PROCESSING OPTIONS", cmd)
        return if (resp.ok) Tlv.parse(resp.data) else null
    }

    private fun readRecordsFromAfl(isoDep: IsoDep, afl: ByteArray): List<TlvNode> {
        val out = ArrayList<TlvNode>()
        var i = 0
        while (i + 4 <= afl.size) {
            val sfi = (afl[i].toInt() and 0xFF) ushr 3
            val first = afl[i + 1].toInt() and 0xFF
            val last = afl[i + 2].toInt() and 0xFF
            i += 4
            if (sfi == 0 || first == 0) continue
            for (rec in first..last) {
                val p2 = (sfi shl 3) or 0x04
                val cmd = buildApdu(0x00, 0xB2, rec, p2, null, 0x00)
                val resp = transceive(isoDep, "READ RECORD SFI=$sfi #$rec", cmd)
                if (resp.ok) out.addAll(Tlv.parse(resp.data))
            }
        }
        return out
    }

    private fun readTransactionLog(isoDep: IsoDep, tlv: List<TlvNode>): List<TxLogEntry> {
        val logEntry = Tlv.findFirst(tlv, "9F4D")?.value
            ?: Tlv.findFirst(tlv, "DF60")?.value // một số thẻ dùng tag riêng
        val logFormat = Tlv.findFirst(tlv, "9F4F")?.value
        if (logEntry == null || logEntry.size < 2) return emptyList()
        val sfi = logEntry[0].toInt() and 0xFF
        val count = logEntry[1].toInt() and 0xFF
        val entries = ArrayList<TxLogEntry>()
        for (rec in 1..count) {
            val p2 = (sfi shl 3) or 0x04
            val cmd = buildApdu(0x00, 0xB2, rec, p2, null, 0x00)
            val resp = transceive(isoDep, "READ LOG SFI=$sfi #$rec", cmd)
            if (resp.ok && resp.data.isNotEmpty()) {
                entries.add(parseLogRecord(resp.data, logFormat))
            }
        }
        return entries
    }

    private fun parseLogRecord(data: ByteArray, format: ByteArray?): TxLogEntry {
        var amount: String? = null
        var currency: String? = null
        var date: String? = null
        var type: String? = null
        if (format != null) {
            val dol = parseDol(format)
            var pos = 0
            for ((tag, len) in dol) {
                if (pos + len > data.size) break
                val v = data.copyOfRange(pos, pos + len)
                pos += len
                when (tag.uppercase()) {
                    "9F02" -> amount = bcdAmount(v)
                    "5F2A" -> currency = CurrencyCodes.name(Hex.toHex(v))
                    "9A" -> date = bcdDate(v)
                    "9C" -> type = txType(v)
                }
            }
        }
        return TxLogEntry(amount, currency, date, type, Hex.toHexSpaced(data))
    }

    private fun getDataInt(isoDep: IsoDep, tag: String, label: String): Int? {
        val t = Hex.fromHex(tag)
        val p1 = t[0].toInt() and 0xFF
        val p2 = if (t.size > 1) t[1].toInt() and 0xFF else 0x00
        val cmd = buildApdu(0x80, 0xCA, p1, p2, null, 0x00)
        val resp = transceive(isoDep, label, cmd)
        if (!resp.ok) return null
        val node = Tlv.parse(resp.data).let { Tlv.findFirst(it, tag) }
        val value = node?.value ?: return null
        var n = 0
        for (b in value) n = (n shl 8) or (b.toInt() and 0xFF)
        return n
    }

    // ---- Tiện ích APDU ----

    private data class ApduResult(val data: ByteArray, val sw1: Int, val sw2: Int) {
        val ok: Boolean get() = sw1 == 0x90 && sw2 == 0x00
        val sw: String get() = "%02X%02X".format(sw1, sw2)
    }

    private fun transceive(isoDep: IsoDep, label: String, cmd: ByteArray): ApduResult {
        return try {
            var resp = isoDep.transceive(cmd)
            // Xử lý "61xx" (còn dữ liệu) và "6Cxx" (Le sai) nếu gặp
            resp = handleProcedureBytes(isoDep, cmd, resp)
            val result = split(resp)
            log.add(ApduExchange(label, Hex.toHexSpaced(cmd), Hex.toHexSpaced(result.data), result.sw))
            result
        } catch (e: Exception) {
            log.add(ApduExchange(label, Hex.toHexSpaced(cmd), "LỖI: ${e.message}", "----"))
            ApduResult(ByteArray(0), 0x6F, 0x00)
        }
    }

    private fun handleProcedureBytes(isoDep: IsoDep, cmd: ByteArray, resp: ByteArray): ByteArray {
        if (resp.size < 2) return resp
        val sw1 = resp[resp.size - 2].toInt() and 0xFF
        val sw2 = resp[resp.size - 1].toInt() and 0xFF
        if (sw1 == 0x61) {
            val get = byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, sw2.toByte())
            return isoDep.transceive(get)
        }
        if (sw1 == 0x6C) {
            val retry = cmd.copyOf()
            retry[retry.size - 1] = sw2.toByte()
            return isoDep.transceive(retry)
        }
        return resp
    }

    private fun split(resp: ByteArray): ApduResult {
        if (resp.size < 2) return ApduResult(ByteArray(0), 0x6F, 0x00)
        val data = resp.copyOfRange(0, resp.size - 2)
        return ApduResult(data, resp[resp.size - 2].toInt() and 0xFF, resp[resp.size - 1].toInt() and 0xFF)
    }

    private fun buildApdu(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray?, le: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(cla); out.write(ins); out.write(p1); out.write(p2)
        if (data != null && data.isNotEmpty()) {
            out.write(data.size)
            out.write(data)
        }
        out.write(le)
        return out.toByteArray()
    }

    // ---- Trích xuất / giải mã ----

    private fun extractApplications(tlv: List<TlvNode>): List<EmvApplication> {
        val apps = ArrayList<EmvApplication>()
        for (node in Tlv.flatten(tlv)) {
            if (node.tag.equals("61", true)) {
                val aid = node.find("4F")?.value?.let { Hex.toHex(it) } ?: continue
                val label = node.find("50")?.value?.let { String(it, Charsets.US_ASCII).trim() }
                val pref = node.find("9F12")?.value?.let { String(it, Charsets.US_ASCII).trim() }
                apps.add(EmvApplication(aid, label, pref, schemeOfAid(aid)))
            }
        }
        return apps.distinctBy { it.aid }
    }

    private fun labelOf(fci: List<TlvNode>) =
        Tlv.findFirst(fci, "50")?.value?.let { String(it, Charsets.US_ASCII).trim() }

    private fun prefNameOf(fci: List<TlvNode>) =
        Tlv.findFirst(fci, "9F12")?.value?.let { String(it, Charsets.US_ASCII).trim() }

    private fun buildTagViews(tlv: List<TlvNode>): List<EmvTagView> {
        val seen = LinkedHashMap<String, EmvTagView>()
        for (n in Tlv.flatten(tlv)) {
            if (n.constructed) continue
            if (seen.containsKey(n.tag)) continue
            val ascii = Hex.toAscii(n.value).takeIf { it.any { c -> c.isLetterOrDigit() } }
            seen[n.tag] = EmvTagView(n.tag, EmvTags.nameOf(n.tag), Hex.toHexSpaced(n.value), ascii)
        }
        return seen.values.toList()
    }

    private fun decodePan(v: ByteArray): String {
        val s = Hex.toHex(v)
        return s.trimEnd('F').filter { it.isDigit() }
    }

    private fun track2Pan(v: ByteArray): String? {
        val s = Hex.toHex(v)
        val sep = s.indexOfFirst { it == 'D' || it == 'd' }
        return if (sep > 0) s.substring(0, sep) else null
    }

    private fun track2Expiry(v: ByteArray): String? {
        val s = Hex.toHex(v)
        val sep = s.indexOfFirst { it == 'D' || it == 'd' }
        return if (sep > 0 && s.length >= sep + 5) {
            val yymm = s.substring(sep + 1, sep + 5)
            "${yymm.substring(2, 4)}/${yymm.substring(0, 2)}"
        } else null
    }

    private fun yymmddToMonthYear(v: ByteArray): String? {
        val s = Hex.toHex(v)
        if (s.length < 4) return null
        val yy = s.substring(0, 2)
        val mm = s.substring(2, 4)
        return "$mm/$yy"
    }

    private fun priorityOf(aid: String): Int = when (schemeOfAid(aid)) {
        "Visa" -> 1
        "Mastercard" -> 2
        "JCB" -> 3
        "UnionPay" -> 4
        "NAPAS" -> 5
        else -> 9
    }

    private fun schemeOfAid(aid: String): String {
        val a = aid.uppercase()
        return when {
            a.startsWith("A000000003") -> "Visa"
            a.startsWith("A000000004") -> "Mastercard"
            a.startsWith("A000000065") -> "JCB"
            a.startsWith("A000000333") -> "UnionPay"
            a.startsWith("A000000727") -> "NAPAS"
            a.startsWith("A000000025") -> "American Express"
            else -> "Không rõ"
        }
    }

    // ---- DOL (Data Object List) ----

    private fun parseDol(dol: ByteArray): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        var i = 0
        while (i < dol.size) {
            val tagStart = i
            val first = dol[i].toInt() and 0xFF
            i++
            if ((first and 0x1F) == 0x1F) {
                while (i < dol.size && (dol[i].toInt() and 0x80) != 0) i++
                if (i < dol.size) i++
            }
            if (i >= dol.size) break
            val tag = Hex.toHex(dol.copyOfRange(tagStart, i))
            val len = dol[i].toInt() and 0xFF
            i++
            out.add(tag to len)
        }
        return out
    }

    private fun buildDolValues(dol: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for ((tag, len) in parseDol(dol)) {
            val value = DOL_DEFAULTS[tag.uppercase()]?.let { adjust(it, len) } ?: ByteArray(len)
            out.write(value)
        }
        return out.toByteArray()
    }

    private fun adjust(src: ByteArray, len: Int): ByteArray = when {
        src.size == len -> src
        src.size > len -> src.copyOfRange(src.size - len, src.size)
        else -> ByteArray(len).also { System.arraycopy(src, 0, it, len - src.size, src.size) }
    }

    private fun bcdAmount(v: ByteArray): String? {
        val s = Hex.toHex(v).filter { it.isDigit() }
        val n = s.toLongOrNull() ?: return null
        return "%,.2f".format(n / 100.0)
    }

    private fun bcdDate(v: ByteArray): String? {
        val s = Hex.toHex(v)
        return if (s.length >= 6) "20${s.substring(0, 2)}-${s.substring(2, 4)}-${s.substring(4, 6)}" else null
    }

    private fun txType(v: ByteArray): String = when (Hex.toHex(v)) {
        "00" -> "Mua hàng"
        "01" -> "Rút tiền mặt"
        "09" -> "Mua kèm rút tiền"
        "20" -> "Hoàn tiền"
        else -> "Mã ${Hex.toHex(v)}"
    }

    companion object {
        private const val PPSE = "2PAY.SYS.DDF01"

        private val WELL_KNOWN_AIDS = listOf(
            "A0000000031010" to "Visa",
            "A0000000041010" to "Mastercard",
            "A00000000410101213" to "Mastercard",
            "A0000000651010" to "JCB",
            "A000000333010101" to "UnionPay",
            "A000000727101000" to "NAPAS",
            "A0000000250000" to "American Express"
        )

        /** Giá trị mặc định khi thẻ yêu cầu dữ liệu đầu cuối trong PDOL/GPO. */
        private val DOL_DEFAULTS: Map<String, ByteArray> = mapOf(
            "9F66" to Hex.fromHex("36204000"),           // TTQ (Visa)
            "9F02" to Hex.fromHex("000000000000"),       // số tiền ủy quyền = 0
            "9F03" to Hex.fromHex("000000000000"),       // số tiền khác = 0
            "9F1A" to Hex.fromHex("0704"),               // mã nước đầu cuối = VN
            "95"   to Hex.fromHex("0000000000"),         // TVR
            "5F2A" to Hex.fromHex("0704"),               // mã tiền tệ giao dịch = VND
            "9A"   to Hex.fromHex("260101"),             // ngày giao dịch (YYMMDD)
            "9C"   to Hex.fromHex("00"),                 // loại giao dịch = mua hàng
            "9F37" to Hex.fromHex("12345678"),           // số ngẫu nhiên không đoán trước
            "9F35" to Hex.fromHex("22"),                 // loại đầu cuối
            "9F45" to Hex.fromHex("0000"),
            "9F4C" to Hex.fromHex("0000000000000000"),
            "9F34" to Hex.fromHex("000000"),
            "9F21" to Hex.fromHex("000000"),
            "9F7C" to Hex.fromHex("0000000000000000000000000000000000000000000000000000000000000000"),
            "9F40" to Hex.fromHex("0000000000")
        )
    }
}
