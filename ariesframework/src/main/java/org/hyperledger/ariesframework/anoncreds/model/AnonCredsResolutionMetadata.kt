package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsResolutionMetadata(
    val error: String? = null,
    val message: String? = null,
    val extensible: Map<String, @Contextual Any> = emptyMap()
)