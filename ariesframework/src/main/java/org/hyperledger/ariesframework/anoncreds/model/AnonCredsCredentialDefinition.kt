package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

@Serializable
data class AnonCredsCredentialDefinition(
    val issuerId: String,
    val schemaId: String,
    @SerialName("type")
    val type: String = "CL", // pode-se usar enum se quiser restringir a valores válidos
    val tag: String,
    val value: CredentialDefinitionValue,
) {

    @OptIn(ExperimentalSerializationApi::class)
    fun toJson(): String = Json {
        prettyPrint = true
        encodeDefaults = true
    }.encodeToString(serializer<AnonCredsCredentialDefinition>(), this)
}

@Serializable
data class CredentialDefinitionValue(
    val primary: Map<String, JsonElement>,
    val revocation: JsonElement? = null,
)

// data class CredentialDefinitionValue(
//    @Serializable(with = AnyMapSerializer::class)
//    val primary: Map<String, Any>,
//    @Serializable(with = AnyValueSerializer::class)
//    val revocation: Any? = null
// )
