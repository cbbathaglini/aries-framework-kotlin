package org.hyperledger.ariesframework.anoncreds.model.holder

import kotlinx.serialization.Serializable

@Serializable
data class GetCredentialsForProofRequestReturn(
    val credentials: List<CredentialForProofRequest>
)

@Serializable
data class CredentialForProofRequest(
    val credentialInfo: AnonCredsCredentialInfo,
    val interval: AnonCredsNonRevokedInterval? = null
)

@Serializable
data class AnonCredsNonRevokedInterval(
    val from: Long? = null,
    val to: Long? = null
)