package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsSelectedCredentials(
    val attributes: Map<String, AnonCredsRequestedAttributeMatch>,
    val predicates: Map<String, AnonCredsRequestedPredicateMatch>,
    @SerialName("selfAttestedAttributes")
    val selfAttestedAttributes: Map<String, String>
)