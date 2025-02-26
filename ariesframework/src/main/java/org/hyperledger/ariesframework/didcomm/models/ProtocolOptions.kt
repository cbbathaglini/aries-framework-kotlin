package org.hyperledger.ariesframework.didcomm.models

import kotlinx.serialization.Serializable

@Serializable
data class ProtocolOptions(
    val id: String,
    val roles: List<String>? = null
)
