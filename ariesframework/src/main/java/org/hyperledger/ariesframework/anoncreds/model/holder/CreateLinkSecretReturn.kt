package org.hyperledger.ariesframework.anoncreds.model.holder

import kotlinx.serialization.Serializable

@Serializable
data class CreateLinkSecretReturn(
    val linkSecretId: String,
    val linkSecret: String?,
)
