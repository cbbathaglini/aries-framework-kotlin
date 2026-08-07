package org.hyperledger.ariesframework.proofs.models

import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval

data class RequestedItem(
    val nonRevokedInterval: AnonCredsNonRevokedInterval,
    val schemaId: String? = null,
    val credentialDefinitionId: String? = null,
    val revocationRegistryDefinitionId: String? = null,
)
