package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsRevocationStatusList(
    val issuerId: String,
    val revRegDefId: String,
    val revocationList: List<Int>,
    val currentAccumulator: String,
    val timestamp: Long,
)
