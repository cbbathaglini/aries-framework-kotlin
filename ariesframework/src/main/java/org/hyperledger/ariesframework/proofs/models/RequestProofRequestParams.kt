package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.proofs.formats.ProofFormatService
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

@Serializable
data class RequestProofRequestParams(
    val proofRecord: ProofExchangeRecord,
    val proofFormats: List<ProofFormatSpec> = emptyList(),
    val formatServices: List<ProofFormatService<*>>,
    val comment: String? = null,
    val goalCode: String? = null,
    val goal: String? = null,
    val presentMultiple: Boolean? = null,
    val willConfirm: Boolean? = null,
)
