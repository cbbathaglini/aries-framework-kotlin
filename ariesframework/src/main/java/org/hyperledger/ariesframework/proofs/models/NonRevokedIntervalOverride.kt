package org.hyperledger.ariesframework.proofs.models

data class NonRevokedIntervalOverride(
    val revocationRegistryDefinitionId: String,
    val requestedFromTimestamp: ULong,
    val overrideRevocationStatusListTimestamp: ULong,
)
