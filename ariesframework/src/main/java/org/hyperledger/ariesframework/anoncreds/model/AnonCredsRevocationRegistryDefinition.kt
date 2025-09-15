package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@Serializable
data class AnonCredsRevocationRegistryDefinition(
    val issuerId: String,
    val revocDefType: String = "CL_ACCUM",
    val credDefId: String,
    val tag: String,
    val value: RevocationRegistryValue,
) {

    @OptIn(ExperimentalSerializationApi::class)
    fun toJson(): String {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        return json.encodeToString(serializer<AnonCredsRevocationRegistryDefinition>(), this)
    }
}

@Serializable
data class RevocationRegistryValue(
    val publicKeys: PublicKeys,
    val maxCredNum: Int,
    val tailsLocation: String,
    val tailsHash: String,
    val issuanceType: String? = null, // indy
)

@Serializable
data class PublicKeys(
    val accumKey: AccumKey,
)

@Serializable
data class AccumKey(
    val z: String,
)
