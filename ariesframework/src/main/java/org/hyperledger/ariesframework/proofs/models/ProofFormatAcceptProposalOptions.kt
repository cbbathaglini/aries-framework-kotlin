package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

@Serializable
data class ProofFormatAcceptProposalOptions(
    val proofRecord: ProofExchangeRecord,
    val attachmentId: String? = null,
    val proposalAttachment: Attachment,
    val proofFormats: Map<String, JsonElement>? = emptyMap()
)
