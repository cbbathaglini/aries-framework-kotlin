package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

@Serializable
data class ProofFormatAcceptRequestOptions(
    val proofRecord: ProofExchangeRecord,
    val proofFormats: Map<String, JsonElement>? = emptyMap(),
    val attachmentId: String? = null,
    val requestAttachment: Attachment,
    val proposalAttachment: Attachment?
)