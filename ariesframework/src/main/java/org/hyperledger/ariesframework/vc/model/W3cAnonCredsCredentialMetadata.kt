package org.hyperledger.ariesframework.vc.model

import kotlinx.serialization.Serializable

@Serializable
data class W3cAnonCredsCredentialMetadata(
    val methodName: String,
    val credentialRevocationId: String? = null,
    val linkSecretId: String,
)
