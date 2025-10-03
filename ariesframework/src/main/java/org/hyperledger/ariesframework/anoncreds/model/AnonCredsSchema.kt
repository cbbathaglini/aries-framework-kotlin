package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@Serializable
data class AnonCredsSchema(
    val issuerId: String,
    val name: String,
    val version: String,
    val attrNames: List<String>,
) {
    fun toJson(): String {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        return json.encodeToString(serializer<AnonCredsSchema>(), this)
    }

    override fun toString(): String {
        return "AnonCredsSchema(issuerId='$issuerId', name='$name', version='$version', attrNames=$attrNames)"
    }
}
