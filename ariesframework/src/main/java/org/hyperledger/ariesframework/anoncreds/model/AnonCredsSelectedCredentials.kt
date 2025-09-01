package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsSelectedCredentials(
    val attributes: Map<String, AnonCredsRequestedAttributeMatch>,
    val predicates: Map<String, AnonCredsRequestedPredicateMatch>,
    val selfAttestedAttributes: Map<String, String>,
)

@Serializable
data class AnonCredsRequestedAttributeMatch(
    val credentialId: String,
    val timestamp: Long? = null,
    val revealed: Boolean,
    val credentialInfo: AnonCredsCredentialInfo,
    val revoked: Boolean? = null,
)

@Serializable
data class AnonCredsRequestedPredicateMatch(
    val credentialId: String,
    val timestamp: Long? = null,
    val credentialInfo: AnonCredsCredentialInfo,
    val revoked: Boolean? = null,
)
