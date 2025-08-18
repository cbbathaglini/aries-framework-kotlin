package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord

@Serializable
data class CreateProposalProofOptionsV2 (
    val connectionRecord: ConnectionRecord,
    val proofFormats: Map<String, JsonElement> = emptyMap(),
    val comment: String,
    val autoAcceptProof: AutoAcceptProof?,
    val goalCode: String,
    val goal: String,
    val parentThreadId: String,
)