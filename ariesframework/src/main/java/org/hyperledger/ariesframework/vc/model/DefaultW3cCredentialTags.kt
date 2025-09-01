package org.hyperledger.ariesframework.vc.model

import kotlinx.serialization.Serializable

@Serializable
data class DefaultW3cCredentialTags(
    val issuerId: String,
    val subjectIds: List<String>,
    val schemaIds: List<String>,
    val contexts: List<String>,
    val givenId: String? = null,
    val claimFormat: ClaimFormat,
    val proofTypes: List<String>? = null,
    val cryptosuites: List<String>? = null,
    val types: List<String>,
    val algs: List<String>? = null,
)
