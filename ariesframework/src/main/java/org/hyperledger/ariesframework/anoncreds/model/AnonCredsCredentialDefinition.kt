package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AnonCredsCredentialDefinition(
    val issuerId: String,
    val schemaId: String,
    val type: String = "CL", // pode-se usar enum se quiser restringir a valores válidos
    val tag: String,
    val value: CredentialDefinitionValue
)

@Serializable
data class CredentialDefinitionValue(
    val primary: Map<String, JsonElement>,
    val revocation: JsonElement? = null
)

//data class CredentialDefinitionValue(
//    @Serializable(with = AnyMapSerializer::class)
//    val primary: Map<String, Any>,
//    @Serializable(with = AnyValueSerializer::class)
//    val revocation: Any? = null
//)