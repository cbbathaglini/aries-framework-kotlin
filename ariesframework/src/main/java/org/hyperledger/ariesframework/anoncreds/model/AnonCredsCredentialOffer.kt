package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AnonCredsCredentialOffer(
    @SerialName("schema_id")
    val schemaId: String,
    @SerialName("cred_def_id")
    val credDefId: String,
    val nonce: String,
    @SerialName("key_correctness_proof")
    val keyCorrectnessProof: JsonObject
)