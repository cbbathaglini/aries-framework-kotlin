package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsGetCredentialsForRequest (
    val input: AnonCredsGetCredentialsForProofRequestOptions,
    val output: AnonCredsCredentialsForProofRequest
)

@Serializable
data class AnonCredsGetCredentialsForProofRequestOptions(
    @SerialName("filterByNonRevocationRequirements")
    val filterByNonRevocationRequirements: Boolean? = null
)

@Serializable
data class AnonCredsCredentialsForProofRequest(
    val attributes: Map<String, List<AnonCredsRequestedAttributeMatch>>,
    val predicates: Map<String, List<AnonCredsRequestedPredicateMatch>>
)