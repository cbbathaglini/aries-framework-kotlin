package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@Serializable
data class AnonCredsCredentialRequestMetadata(
    val link_secret_blinding_data: AnonCredsLinkSecretBlindingData,
    val link_secret_name: String,
    val nonce: String,
) {

    @OptIn(ExperimentalSerializationApi::class)
    fun toJson(): String {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        return json.encodeToString(serializer<AnonCredsCredentialRequestMetadata>(), this)
    }

    override fun toString(): String {
        return "AnonCredsCredentialRequestMetadata(link_secret_blinding_data=$link_secret_blinding_data, link_secret_name='$link_secret_name', nonce='$nonce')"
    }

    companion object {
        fun fromJsonString(json: String): AnonCredsCredentialRequestMetadata =
            Json.decodeFromString(json)
    }
}
