package org.hyperledger.ariesframework.anoncreds.utils

import java.math.BigInteger
import java.security.MessageDigest

class AnonCredsEncoder {

    companion object{
        fun encodeCredentialValue(value: Any?): String {
            val isEmptyString = value is String && value.isEmpty()

            // Boolean: converte para número (true → 1, false → 0)
            if (value is Boolean) {
                return if (value) "1" else "0"
            }

            // Int32: mantém como string
            if (value is Int) {
                return value.toString()
            }

            // String representando Int32
            if (value is String && !isEmptyString && value.toIntOrNull() != null) {
                val intVal = value.toIntOrNull()
                if (intVal != null) return intVal.toString()
            }

            // Null ou indefinido: retorna 'None'
            val normalized = when (value) {
                null -> "None"
                is Number -> value.toString()
                else -> value.toString()
            }

            // Codifica como SHA-256, inverte os bytes e converte para BigInteger
            val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
            val reversed = digest.reversedArray()
            val bigint = BigInteger(1, reversed)

            return bigint.toString()
        }
    }
}