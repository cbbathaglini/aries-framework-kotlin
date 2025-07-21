package org.hyperledger.ariesframework.didcomm.models

import kotlinx.serialization.Serializable

@Serializable
data class FeatureOptions(
    val id: String,
    val type: String,
)
