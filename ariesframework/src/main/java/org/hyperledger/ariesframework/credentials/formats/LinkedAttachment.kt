package org.hyperledger.ariesframework.credentials.formats

import android.util.Base64
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.error.CredoError
import java.math.BigInteger
import java.security.MessageDigest

@Serializable
data class LinkedAttachment(
    val attributeName: String,
    val attachment: Attachment,
) {
    companion object {
        fun fromOptions(options: LinkedAttachmentOptions): LinkedAttachment {
            val updatedAttachment = options.attachment.copy(
                id = getId(options.attachment),
            )
            return LinkedAttachment(
                attributeName = options.name,
                attachment = updatedAttachment,
            )
        }

        private fun getId(attachment: Attachment): String {
            val encoded = encodeAttachment(attachment)
            return encoded.split(":").getOrNull(1)?.take(64) ?: ""
        }

        fun encodeAttachment(
            attachment: Attachment,
            hashAlgorithm: String = "sha-256",
            baseName: String = "base58btc",
        ): String {
            val data = attachment.data

            val sha = data.sha256
            if (sha != null) {
                return "hl:$sha"
            }

            // ✅ evita smart-cast em var (data.base64)
            val b64 = data.base64
            if (b64 != null) {
                val bytes = TypedArrayEncoder.fromBase64(b64)
                return HashlinkEncoder.encode(bytes, hashAlgorithm, baseName)
            }

            if (data.json != null) {
                throw CredoError("Attachment: (${attachment.id}) has JSON encoded data. This is currently not supported")
            }

            throw CredoError("Attachment: (${attachment.id}) has no data to create a link with")
        }

        fun isLinkedAttachment(attachment: Attachment): Boolean {
            return HashlinkEncoder.isValid("hl:${attachment.id}")
        }
    }
}

object TypedArrayEncoder {
    fun fromBase64(base64: String): ByteArray {
        // NO_WRAP costuma ser melhor pra payloads DIDComm/attachments
        return Base64.decode(base64, Base64.NO_WRAP)
    }
}

object HashlinkEncoder {
    fun encode(data: ByteArray, hashAlgorithm: String, baseName: String): String {
        val alg = normalizeHashAlg(hashAlgorithm)
        val digest = MessageDigest.getInstance(alg).digest(data)

        // ⚠️ aqui você está ignorando baseName na prática (só base58btc)
        val encoded = base58btcEncode(digest)
        return "hl:$encoded"
    }

    fun isValid(hashlink: String): Boolean {
        return hashlink.startsWith("hl:") && hashlink.length > 3
    }

    private fun normalizeHashAlg(input: String): String {
        // aceitando os dois jeitos comuns
        return when (input.lowercase()) {
            "sha-256", "sha256" -> "SHA-256"
            "sha-512", "sha512" -> "SHA-512"
            else -> input // deixa passar (pode estourar e mostrar erro real)
        }
    }

    private fun base58btcEncode(input: ByteArray): String {
        val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

        var intData = input.fold(BigInteger.ZERO) { acc, byte ->
            (acc shl 8) + (byte.toInt() and 0xFF).toBigInteger()
        }

        val sb = StringBuilder()
        while (intData > BigInteger.ZERO) {
            val divRem = intData.divideAndRemainder(58.toBigInteger())
            sb.append(ALPHABET[divRem[1].toInt()])
            intData = divRem[0]
        }

        // zeros à esquerda
        input.takeWhile { it == 0.toByte() }.forEach { sb.append(ALPHABET[0]) }

        return sb.reverse().toString()
    }
}

// import android.util.Base64
// import kotlinx.serialization.Serializable
// import org.hyperledger.ariesframework.agent.decorators.Attachment
// import org.hyperledger.ariesframework.error.CredoError
// import java.math.BigInteger
//
// @Serializable
// data class LinkedAttachment(
//    val attributeName: String,
//    val attachment: Attachment,
// ) {
//    companion object {
//        fun fromOptions(options: LinkedAttachmentOptions): LinkedAttachment {
//            val updatedAttachment = options.attachment.copy(
//                id = getId(options.attachment),
//            )
//            return LinkedAttachment(
//                attributeName = options.name,
//                attachment = updatedAttachment,
//            )
//        }
//
//        private fun getId(attachment: Attachment): String {
//            // Equivalente à lógica de hashlink do encodeAttachment
//            val encoded = encodeAttachment(attachment)
//            return encoded.split(":").getOrNull(1)?.take(64) ?: ""
//        }
//
//        fun encodeAttachment(
//            attachment: Attachment,
//            hashAlgorithm: String = "sha-256",
//            baseName: String = "base58btc",
//        ): String {
//            val data = attachment.data
//
//            return when {
//                data.sha256 != null -> {
//                    "hl:${data.sha256}"
//                }
//
//                data.base64 != null -> {
//                    //val bytes = TypedArrayEncoder.fromBase64(data.base64)
//                    val bytes = TypedArrayEncoder.fromBase64(data.base64)
//                    HashlinkEncoder.encode(bytes, hashAlgorithm, baseName)
//                }
//
//                data.json != null -> {
//                    throw CredoError("Attachment: (${attachment.id}) has JSON encoded data. This is currently not supported")
//                }
//
//                else -> {
//                    throw CredoError("Attachment: (${attachment.id}) has no data to create a link with")
//                }
//            }
//        }
//
//        fun isLinkedAttachment(attachment: Attachment): Boolean {
//            return HashlinkEncoder.isValid("hl:${attachment.id}")
//        }
//    }
// }
//
// object TypedArrayEncoder {
//    fun fromBase64(base64: String): ByteArray {
//        return Base64.decode(base64, Base64.DEFAULT)
//    }
// }
//
// object HashlinkEncoder {
//    fun encode(data: ByteArray, hashAlgorithm: String, baseName: String): String {
//        val digest = java.security.MessageDigest.getInstance(hashAlgorithm).digest(data)
//        val encoded = base58btcEncode(digest) // precisa implementar ou usar lib
//        return "hl:$encoded"
//    }
//
//    fun isValid(hashlink: String): Boolean {
//        return hashlink.startsWith("hl:") && hashlink.length > 3
//    }
//
//    private fun base58btcEncode(input: ByteArray): String {
//        val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
//
//        var intData = input.fold(0.toBigInteger()) { acc, byte ->
//            (acc shl 8) + (byte.toInt() and 0xFF).toBigInteger()
//        }
//
//        val sb = StringBuilder()
//        while (intData > BigInteger.ZERO) {
//            val divRem = intData.divideAndRemainder(58.toBigInteger())
//            sb.append(ALPHABET[divRem[1].toInt()])
//            intData = divRem[0]
//        }
//
//        // Lida com zeros à esquerda
//        input.takeWhile { it == 0.toByte() }.forEach { sb.append(ALPHABET[0]) }
//
//        return sb.reverse().toString()
//    }
// }
