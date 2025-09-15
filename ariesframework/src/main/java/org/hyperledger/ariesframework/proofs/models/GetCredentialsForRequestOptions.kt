package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

@Serializable
data class GetCredentialsForRequestOptions(
    val proofRecord: ProofExchangeRecord,
    val proofFormats: Map<String, JsonElement> = emptyMap(),
)
