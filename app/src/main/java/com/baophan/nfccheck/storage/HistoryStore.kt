package com.baophan.nfccheck.storage

import android.content.Context
import com.baophan.nfccheck.model.ScanResult
import com.baophan.nfccheck.model.ScanType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryIndexEntry(
    val id: String,
    val type: ScanType,
    val timeMillis: Long,
    val scheme: String,
    val subtitle: String?,
    val gated: Boolean        // true nếu cần nhập 4 số cuối mới xem được
)

/** Kết quả khi mở một bản ghi bị khóa bằng 4 số cuối. */
sealed class UnlockResult {
    data class Success(val result: ScanResult) : UnlockResult()
    object WrongDeleted : UnlockResult()   // nhập sai -> đã xóa bản ghi
    object NotFound : UnlockResult()
}

/**
 * Lưu lịch sử quét dưới dạng file mã hóa (AES-GCM, khóa trong Keystore).
 * - Chỉ mục (index) chỉ chứa thông tin KHÔNG nhạy cảm: loại thẻ, thời gian, tổ chức, ngân hàng.
 * - Toàn bộ số thẻ/CCCD nằm trong khối đã mã hóa.
 * - Muốn xem bản ghi thẻ thanh toán/CCCD phải nhập đúng 4 số cuối; SAI 1 LẦN là xóa bản ghi đó.
 */
class HistoryStore(context: Context) {

    private val dir = File(context.filesDir, "history").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")

    fun list(): List<HistoryIndexEntry> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            val out = ArrayList<HistoryIndexEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    HistoryIndexEntry(
                        id = o.getString("id"),
                        type = ScanType.valueOf(o.getString("type")),
                        timeMillis = o.getLong("time"),
                        scheme = o.optString("scheme", ""),
                        subtitle = if (o.isNull("subtitle")) null else o.optString("subtitle", null),
                        gated = o.optBoolean("gated", true)
                    )
                )
            }
            out.sortedByDescending { it.timeMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(result: ScanResult) {
        val blob = Crypto.encrypt(ScanJson.toJson(result).toString().toByteArray(Charsets.UTF_8))
        File(dir, "${result.id}.bin").writeBytes(blob)

        val entries = list().toMutableList()
        entries.removeAll { it.id == result.id }
        val gated = result.type != ScanType.TAG && result.last4 != null
        entries.add(
            HistoryIndexEntry(
                id = result.id,
                type = result.type,
                timeMillis = result.timeMillis,
                scheme = result.titleScheme,
                subtitle = result.subtitle,
                gated = gated
            )
        )
        writeIndex(entries)
    }

    /** Mở bản ghi không bị khóa (thẻ NFC thường). */
    fun load(id: String): ScanResult? {
        val f = File(dir, "$id.bin")
        if (!f.exists()) return null
        return try {
            val json = String(Crypto.decrypt(f.readBytes()), Charsets.UTF_8)
            ScanJson.fromJson(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    /** Mở bản ghi bị khóa bằng 4 số cuối. Sai 1 lần -> xóa luôn bản ghi. */
    fun unlock(id: String, enteredLast4: String): UnlockResult {
        val result = load(id) ?: return UnlockResult.NotFound
        val real = result.last4
        if (real == null) return UnlockResult.Success(result) // không có gì để khóa
        return if (enteredLast4.filter { it.isDigit() } == real) {
            UnlockResult.Success(result)
        } else {
            delete(id)
            UnlockResult.WrongDeleted
        }
    }

    fun delete(id: String) {
        File(dir, "$id.bin").delete()
        writeIndex(list().filterNot { it.id == id })
    }

    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
        indexFile.delete()
    }

    private fun writeIndex(entries: List<HistoryIndexEntry>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("type", it.type.name)
                put("time", it.timeMillis)
                put("scheme", it.scheme)
                put("subtitle", it.subtitle ?: JSONObject.NULL)
                put("gated", it.gated)
            })
        }
        indexFile.writeText(arr.toString())
    }
}
