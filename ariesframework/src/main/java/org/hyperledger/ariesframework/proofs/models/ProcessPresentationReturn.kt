package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable

@Serializable
data class ProcessPresentationReturn(
    val isValid: Boolean = true,
    val message: String? = null,
)
