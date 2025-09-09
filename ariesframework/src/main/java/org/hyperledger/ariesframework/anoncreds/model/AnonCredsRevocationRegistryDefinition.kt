package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.anoncreds.formats.model.CredentialEntryResult

@Serializable
data class AnonCredsRevocationRegistryDefinition(
    val issuerId: String,
    val revocDefType: String = "CL_ACCUM",
    val credDefId: String,
    val tag: String,
    val value: RevocationRegistryValue
){

    fun toJson(): String = Json {
        prettyPrint = true
        encodeDefaults = true
    }.encodeToString(AnonCredsRevocationRegistryDefinition.serializer(), this)
}

@Serializable
data class RevocationRegistryValue(
    val publicKeys: PublicKeys,
    val maxCredNum: Int,
    val tailsLocation: String,
    val tailsHash: String,
    val issuanceType: String? = null //indy
)

@Serializable
data class PublicKeys(
    val accumKey: AccumKey
)

@Serializable
data class AccumKey(
    val z: String
)
