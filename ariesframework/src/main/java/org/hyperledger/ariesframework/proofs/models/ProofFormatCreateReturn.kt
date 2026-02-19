package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.decorators.Attachment

@Serializable
data class ProofFormatCreateReturn(
    val format: ProofFormatSpec,
    val attachment: Attachment,
)
