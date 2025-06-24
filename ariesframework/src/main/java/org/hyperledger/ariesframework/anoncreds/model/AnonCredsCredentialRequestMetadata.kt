package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsCredentialRequestMetadata(
    val link_secret_blinding_data: AnonCredsLinkSecretBlindingData,
    val link_secret_name: String,
    val nonce: String
)