package org.hyperledger.ariesframework.anoncreds.formats.utils

import org.hyperledger.ariesframework.anoncreds.model.issuer.AnonCredsCredentialValue
import org.hyperledger.ariesframework.anoncreds.model.issuer.AnonCredsCredentialValues
import org.hyperledger.ariesframework.anoncreds.utils.AnonCredsEncoder
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute

class Credential {

    companion object {

        fun convertAttributesToCredentialValues(
            attributes: List<CredentialPreviewAttribute>,
        ): AnonCredsCredentialValues {
            return attributes.associate { attribute ->
                attribute.name to AnonCredsCredentialValue(
                    raw = attribute.value,
                    encoded = AnonCredsEncoder.encodeCredentialValue(attribute.value),
                )
            }
        }

        fun assertCredentialValuesMatch(
            firstValues: Map<String, AnonCredsCredentialValue>,
            secondValues: Map<String, AnonCredsCredentialValue>,
        ) {
            val firstKeys = firstValues.keys
            val secondKeys = secondValues.keys

            if (firstKeys.size != secondKeys.size) {
                throw IllegalArgumentException(
                    "Number of values in first entry (${firstKeys.size}) does not match number of values in second entry (${secondKeys.size})",
                )
            }

            for (key in firstKeys) {
                val firstValue = firstValues[key]
                val secondValue = secondValues[key]

                if (secondValue == null) {
                    throw IllegalArgumentException("Second cred values object has no value for key '$key'")
                }

                if (firstValue?.encoded != secondValue.encoded) {
                    throw IllegalArgumentException("Encoded credential values for key '$key' do not match")
                }

                if (firstValue.raw != secondValue.raw) {
                    throw IllegalArgumentException("Raw credential values for key '$key' do not match")
                }
            }
        }

        fun checkCredentialValuesMatch(
            firstValues: Map<String, AnonCredsCredentialValue>,
            secondValues: Map<String, AnonCredsCredentialValue>,
        ): Boolean {
            return try {
                assertCredentialValuesMatch(firstValues, secondValues)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
