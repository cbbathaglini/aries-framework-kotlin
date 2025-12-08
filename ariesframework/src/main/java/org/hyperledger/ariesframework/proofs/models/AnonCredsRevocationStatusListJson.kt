package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsRevocationStatusListJson(
    val issuerId: String,
    val revRegDefId: String,
    val timestamp: Long,
    val revocationList: List<Int>,
    val accum: String
)