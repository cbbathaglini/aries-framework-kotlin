package org.hyperledger.ariesframework.vc.proof

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CredentialEntry(
    val credential: JsonObject, //CredentialOrJson,
    val timestamp: Long? = null,
    val revocationState: JsonObject? = null//CredentialRevocationStateOrJson? = null
)