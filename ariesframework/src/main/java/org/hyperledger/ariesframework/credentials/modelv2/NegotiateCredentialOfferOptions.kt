package org.hyperledger.ariesframework.credentials.modelv2

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

@Serializable
data class NegotiateCredentialOfferOptions (
    val credentialExchangeRecord: CredentialExchangeRecord,
    val credentialFormat: Map<String, JsonElement>,
    val autoAcceptCredential: AutoAcceptCredential,
    val comment: String?,
    val goal: String?,
    val goalCode: String?
)
