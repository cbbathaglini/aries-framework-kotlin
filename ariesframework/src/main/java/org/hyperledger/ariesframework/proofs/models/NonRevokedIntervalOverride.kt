package org.hyperledger.ariesframework.proofs.models

data class NonRevokedIntervalOverride(
    val revocationRegistryDefinitionId: String,
    val requestedFromTimestamp: Long,
    val overrideRevocationStatusListTimestamp: Long,
)
