package org.hyperledger.ariesframework.credentials.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

@Serializable
data class AcceptRequestOptionsV2 (
    val credentialExchangeRecord: CredentialExchangeRecord,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String?  = null,
    val goal: String?  = null,
    val goalCode: String?  = null,
    val credentialFormats: Map<String, JsonElement>? = emptyMap(),
)