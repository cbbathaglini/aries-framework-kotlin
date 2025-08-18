package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ProofFormatSpec (
    @SerialName("attach_id")
    val attachmentId: String = UUID.randomUUID().toString(),
    val format: String
)