package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateCredentialRequestOptions(
    val credentialOffer: AnonCredsCredentialOffer,
    val credentialDefinition: AnonCredsCredentialDefinition,
    val linkSecretId: String? = null,
    val useLegacyProverDid: Boolean? = null
)