package org.hyperledger.ariesframework.vc.proof

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential

@Serializable
data class CredentialWithRevocationMetadata(
    var credential: W3cJsonLdVerifiableCredential,
    var nonRevoked: AnonCredsNonRevokedInterval? = null,
)
