package org.hyperledger.ariesframework.vc.proof

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CreatePresentationOptions(
    val presentationRequest: JsonObject,
    val credentials: List<CredentialEntry>,
    val credentialsProve: List<CredentialProve>,
    val selfAttest: Map<String, String>,
    val linkSecret: String,
    val schemas: JsonObject, //Map<String, SchemaOrJson>,
    val credentialDefinitions: JsonObject//Map<String, CredentialDefinitionOrJson>
)