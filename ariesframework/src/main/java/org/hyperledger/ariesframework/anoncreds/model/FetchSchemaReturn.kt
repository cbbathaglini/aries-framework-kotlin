package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class FetchSchemaReturn (
    val schema: AnonCredsSchema,
    val schemaId: String,
    val indyNamespace: String? = null
)
