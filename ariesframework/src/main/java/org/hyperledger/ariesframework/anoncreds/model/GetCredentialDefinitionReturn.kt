package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class GetCredentialDefinitionReturn(
    val credentialDefinition: AnonCredsCredentialDefinition? = null,
    val credentialDefinitionId: String,
    val resolutionMetadata: AnonCredsResolutionMetadata? = null,
    val credentialDefinitionMetadata: Map<String, @Contextual Any> = emptyMap(),
)
