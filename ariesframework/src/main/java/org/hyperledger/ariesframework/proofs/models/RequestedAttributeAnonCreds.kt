package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialInfo

@Serializable
data class RequestedAttributeAnonCreds(
    //@SerialName("cred_id")
    val credentialId: String,
    val timestamp: Int? = null,
    val revealed: Boolean,
    @Transient
    var credentialInfo: AnonCredsCredentialInfo? = null,
    @Transient
    var revoked: Boolean? = null,
)


@Serializable
data class RequestedPredicateAnonCreds(
    //@SerialName("cred_id")
    val credentialId: String,
    val timestamp: Int? = null,
    @Transient
    var credentialInfo: AnonCredsCredentialInfo? = null,
    @Transient
    var revoked: Boolean? = null,
)
