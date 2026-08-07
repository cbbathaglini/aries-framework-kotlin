package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsCredentialDefinitions(
    val credentialDefinitions: Map<String, AnonCredsCredentialDefinition>,
)
