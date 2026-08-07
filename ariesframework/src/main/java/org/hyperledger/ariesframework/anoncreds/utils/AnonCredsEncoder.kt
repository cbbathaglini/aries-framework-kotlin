package org.hyperledger.ariesframework.anoncreds.utils

import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProof
import org.hyperledger.ariesframework.credentials.utils.EncodeHelper
import org.hyperledger.ariesframework.error.CredoError
import java.math.BigInteger
import java.security.MessageDigest

class AnonCredsEncoder {

    companion object {

        fun checkEncodes(proof: AnonCredsProof) {
            for ((referent, attribute) in proof.requestedProof.revealedAttrs) {
                val sample = EncodeHelper.buildEncSample(
                    referent = referent,
                    raw = attribute.raw,
                    encoded = attribute.encoded,
                )

                EncodeHelper.logEncodingSample("ANONCREDS", "VERIFY revealed_attrs", sample)

                if (sample.expected != sample.encoded) {
                    throw CredoError(
                        "Invalid encoded value for attribute. " +
                            "Raw='${sample.raw}', Expected='${sample.expected}', Actual='${sample.encoded}'",
                    )
                }
            }

            for ((groupReferent, group) in proof.requestedProof.revealedAttrGroups.orEmpty()) {
                for ((attributeName, attribute) in group.values) {
                    // here I like to put "groupReferent.attributeName" to make it clear in the log
                    val ref = "$groupReferent.$attributeName"

                    val sample = EncodeHelper.buildEncSample(
                        referent = ref,
                        raw = attribute.raw,
                        encoded = attribute.encoded,
                    )

                    EncodeHelper.logEncodingSample("ANONCREDS", "VERIFY revealed_attr_groups", sample)

                    if (sample.expected != sample.encoded) {
                        throw CredoError(
                            "Invalid encoded value for attribute '$attributeName'. " +
                                "Raw='${sample.raw}', Expected='${sample.expected}', Actual='${sample.encoded}'",
                        )
                    }
                }
            }
        }
        fun encodeCredentialValue(value: Any?): String {
            // 1️⃣ Boolean → "1"/"0"
            if (value is Boolean) {
                return if (value) "1" else "0"
            }

            // 2️⃣ Number handling (similar to JS/credo-ts)
            if (value is Number) {
                val doubleValue = value.toDouble()

                // is int32 exato?
                if (doubleValue.isFinite() &&
                    doubleValue == kotlin.math.floor(doubleValue) &&
                    doubleValue >= Int.MIN_VALUE &&
                    doubleValue <= Int.MAX_VALUE
                ) {
                    return doubleValue.toInt().toString()
                }

                // not int32 -> becomes string and will be hashed
                return sha256ToDecimal(value.toString())
            }

            // String handling
            if (value is String) {
                // valid integer numeric string?
                if (value.isNotEmpty() &&
                    value.matches(Regex("^[+-]?\\d+$"))
                ) {
                    val parsed = value.toLongOrNull()
                    if (parsed != null &&
                        parsed >= Int.MIN_VALUE &&
                        parsed <= Int.MAX_VALUE
                    ) {
                        return parsed.toInt().toString()
                    }
                }

                // any other string (including "") → hash
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
