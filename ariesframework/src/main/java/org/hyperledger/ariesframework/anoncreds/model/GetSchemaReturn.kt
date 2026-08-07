package org.hyperledger.ariesframework.anoncreds.model

data class GetSchemaReturn(
    val schema: AnonCredsSchema? = null,
    val schemaId: String,
    val resolutionMetadata: AnonCredsResolutionMetadata? = null,
    val schemaMetadata: Map<String, Any> = emptyMap(),
    val issuerId: String? = null,
)
