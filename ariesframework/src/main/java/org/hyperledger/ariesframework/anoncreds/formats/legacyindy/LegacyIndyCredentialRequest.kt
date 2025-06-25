package org.hyperledger.ariesframework.anoncreds.formats.legacyindy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class LegacyIndyCredentialRequest(
    @SerialName("prover_did")
    val proverDid: String,

    @SerialName("entropy")
    val entropy: String? = null,

    @SerialName("cred_def_id")
    val credDefId: String,

    @SerialName("blinded_ms")
    val blindedMs: JsonObject,

    @SerialName("blinded_ms_correctness_proof")
    val blindedMsCorrectnessProof: JsonObject,

    @SerialName("nonce")
    val nonce: String
)