package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

typealias AnonCredsClaimRecord = Map<String, String>

@Serializable
data class AnonCredsCredentialInfo(
    val credentialId: String,
    val attributes: AnonCredsClaimRecord,
    val schemaId: String,
    val credentialDefinitionId: String,
    val revocationRegistryId: String? = null,
    val credentialRevocationId: String? = null,
    val methodName: String,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant,
    val linkSecretId: String
)