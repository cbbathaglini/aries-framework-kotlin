package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsAcceptOfferFormat(
    @SerialName("linkSecretId")
    val linkSecretId: String? = null,
)
