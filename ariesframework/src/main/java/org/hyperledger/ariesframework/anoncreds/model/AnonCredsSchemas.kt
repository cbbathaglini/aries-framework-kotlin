package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsSchemas(
    val schemas: Map<String, AnonCredsSchema> = mutableMapOf(),
) {
    override fun toString(): String {
        return "AnonCredsSchemas(schemas=$schemas)"
    }
}
