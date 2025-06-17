package org.hyperledger.ariesframework.credentials.modelv2

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.credentials.formats.CredentialFormat
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

@Serializable
data class OfferCredentialOptions (
    val connectionId: String,
    val comment: String?,
    val goalCode: String?,
    val goal: String?,
    val autoAcceptCredential: AutoAcceptCredential?,
    val protocolVersion: String,
    val credentialFormat: Map<String, JsonElement>
)