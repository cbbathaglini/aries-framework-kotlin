package org.hyperledger.ariesframework.credentials.utils

import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.anoncreds.model.issuer.AnonCredsCredentialValue
import org.hyperledger.ariesframework.credentials.formats.HashlinkEncoder
import org.hyperledger.ariesframework.credentials.formats.LinkedAttachment
import org.hyperledger.ariesframework.credentials.formats.TypedArrayEncoder
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute
import org.hyperledger.ariesframework.error.CredoError
import java.math.BigInteger
import java.security.MessageDigest

class Functions {

    companion object {

        /**
         * Adds attribute(s) to the credential preview that is linked to the given attachment(s)
         */
        fun createAndLinkAttachmentsToPreview(
            attachments: List<LinkedAttachment>,
            previewAttributes: List<CredentialPreviewAttribute>,
        ): List<CredentialPreviewAttribute> {
            val existingAttributeNames = previewAttributes.map { it.name }.toSet()
            val newPreviewAttributes = previewAttributes.toMutableList()

            for (linkedAttachment in attachments) {
                if (linkedAttachment.attributeName in existingAttributeNames) {
                    throw CredoError("linkedAttachment ${linkedAttachment.attributeName} already exists in the preview")
                } else {
                    val encodedValue = encodeAttachment(linkedAttachment.attachment)
                    newPreviewAttributes += CredentialPreviewAttribute(
                        name = linkedAttachment.attributeName,
                        mimeType = linkedAttachment.attachment.mimetype,
                        value = encodedValue,
                    )
                }
            }

            return newPreviewAttributes
        }

        /**
         * Keep in sync with Credo TS:
         * - boolean -> "1"/"0"
         * - int32 number -> number string
         * - int32 numeric string -> number string
         * - else -> sha256(bigint) of string value, with null/undefined -> "None"
         */
        fun encodeCredentialValue(value: Any?): String {
            // Boolean -> "1"/"0"
            if (value is Boolean) return if (value) "1" else "0"

            // Int32 -> number string
            if (value is Int) return value.toString()

            // String numeric int32 -> number string
            if (value is String && value.isNotEmpty()) {
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) {
                    val asInt = trimmed.toIntOrNull()
                    if (asInt != null) return asInt.toString()
                }
            }

            // If number (not int32) -> stringify
            val normalized = when (value) {
                null -> "None"
                is Number -> value.toString()
                else -> value.toString()
            }

            val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
            val bigint = BigInteger(1, digest)
            return bigint.toString()
        }

        fun mapAttributeRawValuesToAnonCredsCredentialValues(
            record: Map<String, Any>,
        ): Map<String, AnonCredsCredentialValue> {
            return record.mapValues { (_, value) ->
                if (value is Map<*, *>) {
                    throw IllegalArgumentException("Unsupported value type: object for W3cAnonCreds Credential")
                }

                AnonCredsCredentialValue(
                    raw = value.toString(),
                    encoded = encodeCredentialValue(value),
                )
            }
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

            // ✅ evita smart cast em var (data.base64)
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
    }
}

// import org.hyperledger.ariesframework.agent.decorators.Attachment
// import org.hyperledger.ariesframework.anoncreds.model.issuer.AnonCredsCredentialValue
// import org.hyperledger.ariesframework.credentials.formats.HashlinkEncoder
// import org.hyperledger.ariesframework.credentials.formats.LinkedAttachment
// import org.hyperledger.ariesframework.credentials.formats.TypedArrayEncoder
// import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute
// import org.hyperledger.ariesframework.error.CredoError
// import java.math.BigInteger
// import java.security.MessageDigest
//
// class Functions {
//
//    /**
//     * Adds attribute(s) to the credential preview that is linked to the given attachment(s)
//     *
//     * @param attachments a list of the attachments that need to be linked to a credential
//     * @param preview the credential previews where the new linked credential has to be appended to
//     *
//     * @returns a modified version of the credential preview with the linked credentials
//     * */
//    companion object {
//        fun createAndLinkAttachmentsToPreview(
//            attachments: List<LinkedAttachment>,
//            previewAttributes: List<CredentialPreviewAttribute>,
//        ): List<CredentialPreviewAttribute> {
//            val existingAttributeNames = previewAttributes.map { it.name }.toSet()
//            val newPreviewAttributes = previewAttributes.toMutableList()
//
//            for (linkedAttachment in attachments) {
//                if (linkedAttachment.attributeName in existingAttributeNames) {
//                    throw CredoError("linkedAttachment ${linkedAttachment.attributeName} already exists in the preview")
//                } else {
//                    val encodedValue = encodeAttachment(linkedAttachment.attachment)
//                    newPreviewAttributes += CredentialPreviewAttribute(
//                        name = linkedAttachment.attributeName,
//                        mimeType = linkedAttachment.attachment.mimetype,
//                        value = encodedValue,
//                    )
//                }
//            }
//
//            return newPreviewAttributes
//        }
//
//        fun encodeCredentialValue(value: Any?): String {
//            // Se for booleano, retorna 1 ou 0
//            if (value is Boolean) {
//                return if (value) "1" else "0"
//            }
//
//            // Se for Int (int32), retorna diretamente como string
//            if (value is Int) {
//                return value.toString()
//            }
//
//            // Se for uma string representando int32
//            if (value is String && value.isNotBlank() && value.toIntOrNull() != null) {
//                val intVal = value.toIntOrNull()
//                if (intVal != null) return intVal.toString()
//            }
//
//            // Se for número (Double, Float, Long, etc.), converte para string
//            val stringValue = when (value) {
//                null -> "None"
//                is Number, is String -> value.toString()
//                else -> value.toString()
//            }
//
//            // Codifica usando SHA-256 e converte para bigint
//            val sha256 = MessageDigest.getInstance("SHA-256")
//            val hashBytes = sha256.digest(stringValue.toByteArray(Charsets.UTF_8))
//
//            val bigint = BigInteger(1, hashBytes) // 1 => positivo
//            return bigint.toString()
//        }
//
//        fun mapAttributeRawValuesToAnonCredsCredentialValues(
//            record: Map<String, Any>,
//        ): Map<String, AnonCredsCredentialValue> {
//            return record.mapValues { (key, value) ->
//                if (value is Map<*, *>) {
//                    throw IllegalArgumentException("Unsupported value type: object for W3cAnonCreds Credential")
//                }
//
//                AnonCredsCredentialValue(
//                    raw = value.toString(),
//                    encoded = encodeCredentialValue(value),
//                )
//            }
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
//                data.base64 != null -> {
//                    val bytes = TypedArrayEncoder.fromBase64(data.base64)
//                    HashlinkEncoder.encode(bytes, hashAlgorithm, baseName)
//                }
//                data.json != null -> {
//                    throw CredoError("Attachment: (${attachment.id}) has JSON encoded data. This is currently not supported")
//                }
//                else -> {
//                    throw CredoError("Attachment: (${attachment.id}) has no data to create a link with")
//                }
//            }
//        }
//    }
// }
