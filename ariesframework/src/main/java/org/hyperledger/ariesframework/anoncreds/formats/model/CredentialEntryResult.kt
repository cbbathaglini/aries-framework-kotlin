package org.hyperledger.ariesframework.anoncreds.formats.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema

@Serializable
data class CredentialEntryResult (
    val linkSecretId: String,
    val credentialEntry: CredentialEntry,
    val credentialId: String
){
    fun toJson(): String = Json {
        prettyPrint = true
        encodeDefaults = true
    }.encodeToString(CredentialEntryResult.serializer(), this)
}

@Serializable
data class CredentialEntry(
    val credential: JsonElement,                // aceita tanto Credential serializado quanto JsonObject
    val timestamp: Long? = null,
    val revocationState: JsonElement? = null
) {
    fun toJson(): String = Json {
        prettyPrint = true
        encodeDefaults = true
    }.encodeToString(CredentialEntry.serializer(), this)
}