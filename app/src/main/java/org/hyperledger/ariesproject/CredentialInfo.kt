package org.hyperledger.ariesproject

import java.util.Date

data class CredentialInfo(
    val id: String,
    val attrs: Map<String, String> = emptyMap(),
    val schemaId: String? = "none",
    val type: List<String> = emptyList(),
    val credentialDefinitionId: String? = "none",
    val revRegId: String? = "not revokable",
    val createdAt: Date
)