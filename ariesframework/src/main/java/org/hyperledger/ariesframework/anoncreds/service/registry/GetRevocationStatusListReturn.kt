package org.hyperledger.ariesframework.anoncreds.service.registry

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsResolutionMetadata
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList

@Serializable
data class GetRevocationStatusListReturn(
    val revocationStatusList: AnonCredsRevocationStatusList? = null,
    val resolutionMetadata: AnonCredsResolutionMetadata? = null,
    val revocationStatusListMetadata: Map<String, @Contextual Any> = emptyMap(), // Extensible
)
