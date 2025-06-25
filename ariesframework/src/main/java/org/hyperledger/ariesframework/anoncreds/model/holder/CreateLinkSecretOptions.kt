package org.hyperledger.ariesframework.anoncreds.model.holder

import kotlinx.serialization.Serializable

@Serializable
data class CreateLinkSecretOptions (
    val linkSecretId: String?
)