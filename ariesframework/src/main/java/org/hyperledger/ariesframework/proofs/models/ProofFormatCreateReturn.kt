package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.proofs.formats.ProofFormat

@Serializable
data class ProofFormatCreateReturn(
    val format: ProofFormat,
    val attachment: Attachment
)