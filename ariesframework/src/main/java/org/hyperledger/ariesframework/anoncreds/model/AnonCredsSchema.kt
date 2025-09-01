package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AnonCredsSchema(
    val issuerId: String,
    val name: String,
    val version: String,
    val attrNames: List<String>
){
    fun toJson(): String = Json {
        prettyPrint = true
        encodeDefaults = true
    }.encodeToString(AnonCredsSchema.serializer(), this)
}