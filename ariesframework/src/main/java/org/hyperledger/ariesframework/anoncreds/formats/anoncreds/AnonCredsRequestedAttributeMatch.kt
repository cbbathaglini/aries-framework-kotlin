package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialInfo

@Serializable
data class AnonCredsRequestedAttributeMatch(
    val credentialId: String,
    val timestamp: Long? = null,
    val revealed: Boolean,
    val credentialInfo: AnonCredsCredentialInfo,
    val revoked: Boolean? = null,
)
