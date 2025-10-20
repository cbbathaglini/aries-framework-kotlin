package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.proofs.v2.formats.ProofFormatService

@Serializable
data class AcceptProofProposalParams(
    val proofRecord: ProofExchangeRecord,
    val proofFormats: Map<String, JsonElement> = emptyMap(),
    val formatServices: List<ProofFormatService<*>>,
    val comment: String? = null,
    val goalCode: String? = null,
    val goal: String? = null,
    val presentMultiple: Boolean? = null,
    val willConfirm: Boolean? = null,
)
