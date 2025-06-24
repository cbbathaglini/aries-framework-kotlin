package org.hyperledger.ariesframework.credentials.modelv2

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

@Serializable
data class CreateCredentialRequestOptions (
    val credentialFormats : Map<String, JsonElement>,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
    val goal: String? = null,
    val goalCode: String? = null,
    val connectionRecord: ConnectionRecord
)