package org.hyperledger.ariesframework.vc.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class W3cCredentialSchema(
    @SerialName("id")
    val id: String,

    @SerialName("type")
    val type: String
)