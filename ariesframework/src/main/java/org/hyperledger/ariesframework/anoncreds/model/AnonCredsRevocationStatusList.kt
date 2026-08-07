package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uniffi.indy_besu_vdr.RevocationStatusList

@Serializable
data class AnonCredsRevocationStatusList(
    val issuerId: String,
    val revRegDefId: String,
    val revocationList: List<Int>,
    val currentAccumulator: String,
    val timestamp: ULong,
) {
    companion object {

        fun fromAnoncreds(uniffiList: anoncreds_uniffi.RevocationStatusList): AnonCredsRevocationStatusList {
            val jsonString = uniffiList.toJson()
            val json = Json { ignoreUnknownKeys = true }
            val parsed = json.decodeFromString<AnonCredsRevocationStatusList>(jsonString)
            return parsed
        }

        fun toAnonCreds(revocation: RevocationStatusList): AnonCredsRevocationStatusList {
            val listInt: List<Int> = revocation.revocationList.map { it.toInt() }
            return AnonCredsRevocationStatusList(
                timestamp = revocation.timestamp,
                issuerId = revocation.issuerId,
                revRegDefId = revocation.revRegDefId,
                revocationList = listInt,
                currentAccumulator = revocation.currentAccumulator,
            )
        }
    }

    fun toJson(pretty: Boolean = false): String {
        val json = if (pretty) {
            Json { prettyPrint = true }
        } else {
            Json
        }
        return json.encodeToString(this)
    }
}
