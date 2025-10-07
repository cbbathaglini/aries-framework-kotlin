package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable
import uniffi.indy_besu_vdr.RevocationStatusList

@Serializable
data class AnonCredsRevocationStatusList(
    val issuerId: String,
    val revRegDefId: String,
    val revocationList: List<Int>,
    val currentAccumulator: String,
    val timestamp: Long,
) {
    companion object {

        fun toAnonCreds(revocation: RevocationStatusList): AnonCredsRevocationStatusList {
            val listInt: List<Int> = revocation.revocationList.map { it.toInt() }
            return AnonCredsRevocationStatusList(
                timestamp = revocation.timestamp.toLong(),
                issuerId = revocation.issuerId,
                revRegDefId = revocation.revRegDefId,
                revocationList = listInt,
                currentAccumulator = revocation.currentAccumulator,
            )
        }
    }
}
