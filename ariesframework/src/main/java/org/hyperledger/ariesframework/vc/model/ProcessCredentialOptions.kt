package org.hyperledger.ariesframework.vc.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequestMetadata
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition

@Serializable
data class ProcessCredentialOptions(
    @Contextual
    val credentialRequestMetadata: AnonCredsCredentialRequestMetadata,

    val linkSecret: String,

    @Contextual
    val revocationRegistryDefinition: AnonCredsRevocationRegistryDefinition? = null,

    @Contextual
    val credentialDefinition: AnonCredsCredentialDefinition,
)
