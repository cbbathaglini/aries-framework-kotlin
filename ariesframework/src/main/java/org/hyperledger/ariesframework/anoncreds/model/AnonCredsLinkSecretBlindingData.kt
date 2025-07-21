package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsLinkSecretBlindingData(
    val v_prime: String,
    val vr_prime: String? = null
)