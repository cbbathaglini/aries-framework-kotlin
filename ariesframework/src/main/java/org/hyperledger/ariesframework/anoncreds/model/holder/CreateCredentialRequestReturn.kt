package org.hyperledger.ariesframework.anoncreds.model.holder

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequestMetadata

@Serializable
data class CreateCredentialRequestReturn(
    var credentialRequest: AnonCredsCredentialRequest,
    val credentialRequestMetadata: AnonCredsCredentialRequestMetadata
)