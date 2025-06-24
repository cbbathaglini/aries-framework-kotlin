package org.hyperledger.ariesframework.anoncreds.model.holder

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsClaimRecord

@Serializable
data class AnonCredsCredentialInfo(
    val credentialId: String,
    val attributes: AnonCredsClaimRecord,
    val schemaId: String,
    val credentialDefinitionId: String,
    val revocationRegistryId: String? = null,
    val credentialRevocationId: String? = null,
    val methodName: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val linkSecretId: String
)