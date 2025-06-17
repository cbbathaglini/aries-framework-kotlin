package org.hyperledger.ariesframework.credentials.modelv2

import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

data class NegotiateCredentialProposalOptions (
    val credentialExchangeRecord: CredentialExchangeRecord,
    val credentialFormats: Map<String, JsonElement>,
    val autoAcceptCredential : AutoAcceptCredential?,
    val comment : String?,
    val goalCode : String?,
    val goal: String?
)