package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CredentialOfferJson(
    @SerialName("schema_id")
    val schemaId: String,
    @SerialName("cred_def_id")
    val credDefId: String,
    @SerialName("key_proof")
    @Contextual val keyProof: Map<String, @Contextual Any>,
)
