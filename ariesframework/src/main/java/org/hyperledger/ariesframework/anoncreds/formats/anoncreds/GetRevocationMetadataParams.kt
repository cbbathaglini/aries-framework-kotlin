package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval

data class GetRevocationMetadataParams(
    val revocationRegistryId: String,
    val nonRevokedInterval: AnonCredsNonRevokedInterval,
    val timestamp: Long? = null,
    val revocationRegistryIndex: Int? = null
)