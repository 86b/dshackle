package io.emeraldpay.dshackle.upstream.ethereum

import io.emeraldpay.dshackle.upstream.asUnsignedByteArray
import java.math.BigInteger

class RLP {
    companion object {
        private const val SIZE_THRESHOLD = 56
        private const val OFFSET_SHORT_ITEM = 0x80
        private const val OFFSET_LONG_ITEM = 0xb7
        private const val OFFSET_SHORT_LIST = 0xc0
        private const val OFFSET_LONG_LIST = 0xf7

        @JvmStatic
        fun encodeBigInt(value: BigInteger) =
            encode(value.asUnsignedByteArray())

        @JvmStatic
        fun encodeString(str: String) =
            encode(str.toByteArray())

        @JvmStatic
        fun encode(srcData: ByteArray?): ByteArray {
            if (srcData == null || srcData.isEmpty()) {
                return byteArrayOf(OFFSET_SHORT_ITEM.toByte())
            }
            if (srcData.size == 1 && (srcData[0].toInt() and 0xFF < OFFSET_SHORT_ITEM)) {
                return srcData
            }
            if (srcData.size < SIZE_THRESHOLD) {
                val length = OFFSET_SHORT_ITEM + srcData.size
                return byteArrayOf(length.toByte()).plus(srcData)
            }
            val len = toMinimalByteArray(srcData.size)
            return byteArrayOf((OFFSET_LONG_ITEM + len.size).toByte()).plus(len).plus(srcData)
        }

        @JvmStatic
        fun encodeStringList(vararg elements: String) =
            encodeList(elements.map { it.toByteArray() }.map { encode(it) })

        @JvmStatic
        fun encodeList(elements: List<ByteArray>): ByteArray {
            val totalLength = elements.sumOf { it.size }
            if (totalLength < SIZE_THRESHOLD) {
                return byteArrayOf((OFFSET_SHORT_LIST + totalLength).toByte()).plus(
                    elements.reduce { a, b -> a.plus(b) }
                )
            }

            val len = toMinimalByteArray(totalLength)
            return byteArrayOf((OFFSET_LONG_LIST + len.size).toByte())
                .plus(len)
                .plus(elements.reduce { a, b -> a.plus(b) })
        }

        private fun toMinimalByteArray(value: Int): ByteArray {
            val encoded = toByteArray(value)
            for (i in encoded.indices) {
                if (encoded[i].toInt() != 0) {
                    return encoded.copyOfRange(i, encoded.size)
                }
            }
            return byteArrayOf()
        }

        private fun toByteArray(value: Int): ByteArray {
            return byteArrayOf(
                (value shr 24 and 0xff).toByte(),
                (value shr 16 and 0xff).toByte(),
                (value shr 8 and 0xff).toByte(),
                (value and 0xff).toByte()
            )
        }
    }
}
