package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProof
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest

@Serializable
data class FormatDataProofAnonCreds(
    val proposal: AnonCredsProofRequest,
    val request: AnonCredsProofRequest,
    val presentation: AnonCredsProof
) {
}