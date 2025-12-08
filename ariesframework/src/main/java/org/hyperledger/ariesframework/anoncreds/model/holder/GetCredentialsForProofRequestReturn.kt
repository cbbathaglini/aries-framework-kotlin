package org.hyperledger.ariesframework.anoncreds.model.holder

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialInfo

@Serializable
data class GetCredentialsForProofRequestReturn(
    val credentials: List<CredentialForProofRequest>,
)

@Serializable
data class CredentialForProofRequest(
    val credentialInfo: AnonCredsCredentialInfo,
    val interval: AnonCredsNonRevokedInterval? = null,
)

@Serializable
data class AnonCredsNonRevokedInterval(
    val from: ULong? = null,
    val to: ULong? = null,
)
