package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

@Serializable
data class AcceptProofRequestOptions(
    val proofRecord: ProofExchangeRecord,
    val proofFormats: List<ProofFormatSpec> = emptyList(),
    val comment: String? = null,
    val goalCode: String? = null,
    val goal: String? = null,
    val autoAcceptProof: AutoAcceptProof? = null,
    val requestedCredentials: Map<String, JsonElement>? = emptyMap(),
    val chosenCredentialId: String? = null
)
