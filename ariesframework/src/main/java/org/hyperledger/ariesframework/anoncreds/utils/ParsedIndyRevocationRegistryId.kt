package org.hyperledger.ariesframework.anoncreds.utils

import kotlinx.serialization.Serializable

@Serializable
data class ParsedIndyRevocationRegistryId (
    val did : String,
    val namespaceIdentifier: String,
    val schemaSeqNo: String,
    val credentialDefinitionTag: String,
    val revocationRegistryTag: String,
    val namespace: String? = null
)