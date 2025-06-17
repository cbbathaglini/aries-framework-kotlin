package org.hyperledger.ariesframework.credentials.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

@Serializable
data class AcceptCredentialProposalOptions(
    val credentialExchangeRecord: CredentialExchangeRecord,
    val credentialFormats: Map<String, JsonElement>? = emptyMap(),
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
    val goal: String? = null,
    val goalCode: String? = null
)