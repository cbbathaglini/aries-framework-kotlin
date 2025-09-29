package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord

@Serializable
data class CreateProofRequestOptions(
    val proofRequest: AnonCredsProofRequest,
    val proofFormats: List<ProofFormatSpec> = emptyList(),
    val connectionRecord: ConnectionRecord? = null,
    val comment: String? = null,
    val goalCode: String? = null,
    val goal: String? = null,
    val autoAcceptProof: AutoAcceptProof,
    val willConfirm: Boolean? = true,
)
