package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

@Serializable
data class CreateProofProblemReportOptions(
    val proofRecord: ProofExchangeRecord,
    val description: String,
)
