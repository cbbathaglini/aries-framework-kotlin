package org.hyperledger.ariesframework.anoncreds.model.holder

import kotlinx.serialization.Serializable

@Serializable
data class StoreLinkSecretOptions (
    val linkSecretId : String,
    val linkSecretValue : String? = null,
    val setAsDefault : Boolean = false
)