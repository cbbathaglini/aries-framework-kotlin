package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.proofs.formats.ProofFormatService
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

@Serializable
data class CreateProofProposalParams (
    val formatServices: List<ProofFormatService<*>>,
    val proofFormats: Map<String, JsonElement> = emptyMap(),
    val proofRecord: ProofExchangeRecord,
    val comment: String? = null,
    val goalCode: String? = null,
    val goal: String? = null
)
