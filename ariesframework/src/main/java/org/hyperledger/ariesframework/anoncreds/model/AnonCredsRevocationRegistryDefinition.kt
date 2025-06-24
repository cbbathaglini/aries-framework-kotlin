package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsRevocationRegistryDefinition(
    val issuerId: String,
    val revocDefType: String = "CL_ACCUM",
    val credDefId: String,
    val tag: String,
    val value: RevocationRegistryValue
)

@Serializable
data class RevocationRegistryValue(
    val publicKeys: PublicKeys,
    val maxCredNum: Int,
    val tailsLocation: String,
    val tailsHash: String
)

@Serializable
data class PublicKeys(
    val accumKey: AccumKey
)

@Serializable
data class AccumKey(
    val z: String
)
