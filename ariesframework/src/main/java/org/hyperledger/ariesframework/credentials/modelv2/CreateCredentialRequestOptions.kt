package org.hyperledger.ariesframework.credentials.modelv2

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

@Serializable
data class CreateCredentialRequestOptions (
    val credentialFormats : Map<String, JsonElement>,
    val autoAcceptCredential: AutoAcceptCredential?,
    val comment: String?,
    val goal: String?,
    val goalCode: String?,
    val connectionRecord: ConnectionRecord
)