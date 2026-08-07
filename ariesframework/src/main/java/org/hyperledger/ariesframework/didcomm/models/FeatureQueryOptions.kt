package org.hyperledger.ariesframework.didcomm.models

import kotlinx.serialization.Serializable

@Serializable
data class FeatureQueryOptions(
    val featureType: String,
    val match: String,
)
