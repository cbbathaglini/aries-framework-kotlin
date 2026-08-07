package org.hyperledger.ariesframework.didcomm.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeatureQuery(
    @SerialName("feature-type") val featureType: String,
    val match: String,
) {
    constructor(options: FeatureQueryOptions) : this(options.featureType, options.match)
}
