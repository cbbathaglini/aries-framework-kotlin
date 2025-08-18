package org.hyperledger.ariesframework.proofs.models

data class TimestampVerificationResult(
    val verified: Boolean,
    val nonRevokedIntervalOverrides: List<NonRevokedIntervalOverride>? = null
)
