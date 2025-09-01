package org.hyperledger.ariesframework.anoncreds.model

data class CreateCredentialRequestOptions(
    val credentialOffer: AnonCredsCredentialOffer,
    val credentialDefinition: String,
    val linkSecretId: String? = null,
    val useLegacyProverDid: Boolean? = null,
)
