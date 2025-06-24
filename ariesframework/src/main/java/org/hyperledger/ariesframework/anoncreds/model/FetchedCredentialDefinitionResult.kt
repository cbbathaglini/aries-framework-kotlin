package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class FetchedCredentialDefinitionResult (
    credentialDefinition: C,
    credentialDefinitionId = credentialDefinitionId,
    indyNamespace = indyNamespaceStr
)