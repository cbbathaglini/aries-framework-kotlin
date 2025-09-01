package org.hyperledger.ariesframework.anoncreds.model

data class GetSchemaReturn(
    val schema: AnonCredsSchema? = null,
    val schemaId: String,
    val resolutionMetadata: AnonCredsResolutionMetadata,
    val schemaMetadata: Map<String, Any>,
)
