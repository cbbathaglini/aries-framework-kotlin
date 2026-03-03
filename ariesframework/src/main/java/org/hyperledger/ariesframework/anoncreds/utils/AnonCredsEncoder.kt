package org.hyperledger.ariesframework.anoncreds.utils

import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.MessageDigest

class AnonCredsEncoder {

    companion object {
        //
        fun encodeCredentialValue(value: Any?): String {

            // 1️⃣ Boolean → "1"/"0"
            if (value is Boolean) {
                return if (value) "1" else "0"
            }

            // 2️⃣ Number handling (similar ao JS/credo-ts)
            if (value is Number) {
                val doubleValue = value.toDouble()

                // is int32 exato?
                if (doubleValue.isFinite()
                    && doubleValue == kotlin.math.floor(doubleValue)
                    && doubleValue >= Int.MIN_VALUE
                    && doubleValue <= Int.MAX_VALUE
                ) {
                    return doubleValue.toInt().toString()
                }

                // não é int32 → vira string e será hasheado
                return sha256ToDecimal(value.toString())
            }

            // 3️⃣ String handling
            if (value is String) {

                // string numérica inteira válida?
                if (value.isNotEmpty()
                    && value.matches(Regex("^[+-]?\\d+$"))
                ) {
                    val parsed = value.toLongOrNull()
                    if (parsed != null &&
                        parsed >= Int.MIN_VALUE &&
                        parsed <= Int.MAX_VALUE
                    ) {
                        return parsed.toInt().toString()
                    }
                }

                // qualquer outra string (inclusive "") → hash
                return sha256ToDecimal(value)
            }

            // 4️⃣ null → "None" → hash
            if (value == null) {
                return sha256ToDecimal("None")
            }

            // 5️⃣ fallback → string → hash
            return sha256ToDecimal(value.toString())
        }

        private fun sha256ToDecimal(input: String): String {
            val digest = MessageDigest
                .getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))

            return BigInteger(1, digest).toString(10)
        }

    }
}
