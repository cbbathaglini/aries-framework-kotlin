package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AnonCredsCredentialRequestMetadata(
    val link_secret_blinding_data: AnonCredsLinkSecretBlindingData,
    val link_secret_name: String,
    val nonce: String
) {
        fun toJsonString(): String = Json.encodeToString(AnonCredsCredentialRequestMetadata.serializer(), this)

        companion object {
        fun fromJsonString(json: String): AnonCredsCredentialRequestMetadata =
            Json.decodeFromString(json)
    }
}