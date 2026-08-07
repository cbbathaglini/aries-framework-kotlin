package org.hyperledger.ariesframework.vc.proof

import kotlinx.serialization.Serializable

@Serializable
data class CredentialProve(
    val entryIndex: Int,
    val referent: String,
    val isPredicate: Boolean,
    val reveal: Boolean,
)
