package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

@Serializable
data class ProofFormatProcessOptions(
    val attachment: Attachment,
    val proofRecord: ProofExchangeRecord
)