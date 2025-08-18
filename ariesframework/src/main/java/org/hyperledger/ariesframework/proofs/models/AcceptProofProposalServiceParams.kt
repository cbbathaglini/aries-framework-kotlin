package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

@Serializable
data class AcceptProofProposalServiceParams (
    val proofRecord: ProofExchangeRecord,
    val proofFormats: Map<String, JsonElement> = emptyMap(),
    val comment: String? = null,
    val goalCode: String? = null,
    val goal: String? = null,
    val autoAcceptProof: AutoAcceptProof,
    val willConfirm : Boolean? = null
)
