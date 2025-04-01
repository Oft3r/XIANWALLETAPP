package com.example.xianwalletapp.network

import java.math.BigInteger

/**
 * Base58 is a way to encode Bitcoin addresses as numbers and letters (base58).
 * Note that this is not the same base58 as used by Flickr, which you may see reference to around the internet.
 *
 * Ported from JavaScript implementation in the Xian web wallet
 */
object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val ALPHABET_MAP = IntArray(128)

    init {
        // Initialize alphabet map with -1
        for (i in ALPHABET_MAP.indices) {
            ALPHABET_MAP[i] = -1
        }
        // Set values for each character in the alphabet
        for (i in ALPHABET.indices) {
            ALPHABET_MAP[ALPHABET[i].code] = i
        }
    }

    /**
     * Encode a byte array to Base58
     */
    fun encode(input: ByteArray): String {
        if (input.isEmpty()) {
            return ""
        }

        // Count leading zeros
        var zeros = 0
        while (zeros < input.size && input[zeros].toInt() == 0) {
            zeros++
        }

        // Convert to BigInteger and encode
        val inputCopy = input.copyOf(input.size)
        var bi = BigInteger(1, inputCopy)
        val sb = StringBuilder()

        // Special case for zero
        if (bi == BigInteger.ZERO) {
            sb.append(ALPHABET[0])
        } else {
            // Convert to base58
            val base = BigInteger.valueOf(58)
            var remainder: BigInteger
            while (bi > BigInteger.ZERO) {
                val divmod = bi.divideAndRemainder(base)
                bi = divmod[0]
                remainder = divmod[1]
                sb.append(ALPHABET[remainder.toInt()])
            }
        }

        // Add leading '1's for each leading zero byte
        for (i in 0 until zeros) {
            sb.append(ALPHABET[0])
        }

        // Reverse the string to get the correct order
        return sb.reverse().toString()
    }

    /**
     * Decode a Base58 string to a byte array
     */
    fun decode(input: String): ByteArray {
        if (input.isEmpty()) {
            return ByteArray(0)
        }

        // Convert from Base58 to decimal
        var bi1 = BigInteger.ZERO
        val base = BigInteger.valueOf(58)

        // Count leading '1's
        var zeros = 0
        while (zeros < input.length && input[zeros] == ALPHABET[0]) {
            zeros++
        }

        // Convert from Base58 to decimal
        var bi2 = BigInteger.ZERO
        for (i in input.indices) {
            val c = input[i]
            val digit = if (c.code < 128) ALPHABET_MAP[c.code] else -1
            if (digit < 0) {
                throw IllegalArgumentException("Invalid Base58 character: $c")
            }
            bi2 = bi2.multiply(base).add(BigInteger.valueOf(digit.toLong()))
        }

        // Convert to byte array
        var bytes = bi2.toByteArray()
        // Remove sign byte if present
        if (bytes.size > 0 && bytes[0] == 0.toByte()) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }

        // Add leading zeros
        val result = ByteArray(zeros + bytes.size)
        System.arraycopy(bytes, 0, result, zeros, bytes.size)

        return result
    }
}