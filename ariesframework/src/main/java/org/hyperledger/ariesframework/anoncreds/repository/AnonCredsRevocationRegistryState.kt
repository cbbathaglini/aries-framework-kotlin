package org.hyperledger.ariesframework.anoncreds.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AnonCredsRevocationRegistryState {
    @SerialName("created")
    Created,

    @SerialName("active")
    Active,

    @SerialName("full")
    Full,
}
