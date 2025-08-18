package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

@Serializable
data class CreateProofRequestOptions(
    val proofRecord: ProofExchangeRecord,
    val proofFormats: Map<String, JsonElement> = emptyMap(),
    val parentThreadId: String? = null,
    val connectionRecord: ConnectionRecord? = null,
    val comment: String? = null,
    val goalCode: String? = null,
    val goal: String? = null,
    val autoAcceptProof: AutoAcceptProof,
    val willConfirm : Boolean? = true
)