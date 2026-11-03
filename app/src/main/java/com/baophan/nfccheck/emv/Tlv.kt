package com.baophan.nfccheck.emv

import com.baophan.nfccheck.util.Hex

/**
 * Một nút BER-TLV.
 * - [tag] là mã tag dạng chuỗi hex (ví dụ "5A", "9F26").
 * - [value] là byte dữ liệu thô.
 * - [children] là các nút con nếu đây là tag "constructed".
 */
data class TlvNode(
    val tag: String,
    val value: ByteArray,
    val constructed: Boolean,
    val children: List<TlvNode>
) {
    fun find(tag: String): TlvNode? {
        if (this.tag.equals(tag, ignoreCase = true)) return this
        for (c in children) {
            val r = c.find(tag)
            if (r != null) return r
        }
        return null
    }

    override fun equals(other: Any?): Boolean =
        other is TlvNode && other.tag == tag && other.value.contentEquals(value)

    override fun hashCode(): Int = tag.hashCode() * 31 + value.contentHashCode()
}

/** Bộ phân tích BER-TLV theo chuẩn EMV. Không ném lỗi: dừng khi dữ liệu không hợp lệ. */
object Tlv {

    fun parse(data: ByteArray): List<TlvNode> = parse(data, 0, data.size)

    private fun parse(data: ByteArray, start: Int, end: Int): List<TlvNode> {
        val nodes = ArrayList<TlvNode>()
        var i = start
        while (i < end) {
            // Bỏ qua byte đệm 0x00 / 0xFF giữa các TLV
            if (data[i].toInt() and 0xFF == 0x00 || data[i].toInt() and 0xFF == 0xFF) {
                i++; continue
            }
            val tagStart = i
            val first = data[i].toInt() and 0xFF
            val constructed = (first and 0x20) != 0
            i++
            // Tag nhiều byte: 5 bit thấp = 11111
            if ((first and 0x1F) == 0x1F) {
                while (i < end && (data[i].toInt() and 0x80) != 0) i++
                if (i < end) i++
            }
            if (i > end) break
            val tag = Hex.toHex(data.copyOfRange(tagStart, i))

            if (i >= end) break
            // Độ dài
            var len = data[i].toInt() and 0xFF
            i++
            if (len and 0x80 != 0) {
                val numBytes = len and 0x7F
                if (numBytes == 0 || numBytes > 4 || i + numBytes > end) break
                len = 0
                for (k in 0 until numBytes) {
                    len = (len shl 8) or (data[i].toInt() and 0xFF)
                    i++
                }
            }
            if (len < 0 || i + len > end) break
            val value = data.copyOfRange(i, i + len)
            i += len

            val children = if (constructed) parse(value, 0, value.size) else emptyList()
            nodes.add(TlvNode(tag, value, constructed, children))
        }
        return nodes
    }

    /** Làm phẳng cây TLV thành danh sách các nút lá/constructed theo thứ tự duyệt. */
    fun flatten(nodes: List<TlvNode>): List<TlvNode> {
        val out = ArrayList<TlvNode>()
        fun walk(n: TlvNode) {
            out.add(n)
            n.children.forEach { walk(it) }
        }
        nodes.forEach { walk(it) }
        return out
    }

    fun findFirst(nodes: List<TlvNode>, tag: String): TlvNode? {
        for (n in nodes) {
            val r = n.find(tag)
            if (r != null) return r
        }
        return null
    }
}
