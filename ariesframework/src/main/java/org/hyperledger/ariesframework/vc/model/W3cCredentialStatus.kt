package org.hyperledger.ariesframework.vc.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class W3cCredentialStatus(
    @SerialName("id")
    val id: String,

    @SerialName("type")
    val type: String,
)
