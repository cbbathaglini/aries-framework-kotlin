package org.hyperledger.ariesframework.cache

import kotlinx.serialization.Serializable

@Serializable
data class RevRegDefDto(
    val issuerId: String,
    val revocDefType: String,
    val credDefId: String,
    val tag: String,
    val value: String,
)