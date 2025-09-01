package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class AnonCredsCredentialRequest(
    @SerialName("prover_did")
    var proverDid: String? = null,

    @SerialName("entropy")
    val entropy: String? = null,

    @SerialName("cred_def_id")
    val credDefId: String,

    @SerialName("blinded_ms")
    val blindedMs: Map<String, JsonElement>,

    @SerialName("blinded_ms_correctness_proof")
    val blindedMsCorrectnessProof: Map<String, JsonElement>,

    @SerialName("nonce")
    val nonce: String,
) {
    fun toJsonString(): String = Json.encodeToString(AnonCredsCredentialRequest.serializer(), this)
    override fun toString(): String {
        return "AnonCredsCredentialRequest(proverDid=$proverDid, entropy=$entropy, credDefId='$credDefId', blindedMs=$blindedMs, blindedMsCorrectnessProof=$blindedMsCorrectnessProof, nonce='$nonce')"
    }

    companion object {
        fun fromJsonString(json: String): AnonCredsCredentialRequest =
            Json.decodeFromString(json)
    }
}
