package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class CredentialDefinitionResult (
   val credentialDefinition: AnonCredsCredentialDefinition,
   val credentialDefinitionId: String,
   val indyNamespace: String? = null
)