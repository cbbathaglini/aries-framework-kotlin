package org.hyperledger.ariesframework.anoncreds.formats.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

@Serializable
data class CredentialEntryResult(
    val linkSecretId: String,
    val credentialEntry: CredentialEntry,
    val credentialId: String,
) {
//    fun toJson(): String = Json {
//        prettyPrint = true
//        encodeDefaults = true
//    }.encodeToString(CredentialEntryResult.serializer(), this)

    @OptIn(ExperimentalSerializationApi::class)
    fun toJson(): String = Json {
        prettyPrint = true
        encodeDefaults = true
    }.encodeToString(serializer<CredentialEntryResult>(), this)
}

@Serializable
data class CredentialEntry(
    val credential: JsonElement, // aceita tanto Credential serializado quanto JsonObject
    val timestamp: ULong? = null,
    val revocationState: JsonElement? = null,
) {
    @OptIn(ExperimentalSerializationApi::class)
    fun toJson(): String = Json {
        prettyPrint = true
        encodeDefaults = true
    }.encodeToString(serializer<CredentialEntry>(), this)
}
