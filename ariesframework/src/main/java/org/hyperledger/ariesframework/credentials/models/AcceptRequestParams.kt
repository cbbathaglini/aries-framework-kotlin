package org.hyperledger.ariesframework.credentials.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord

@Serializable
data class AcceptRequestParams (
    val credentialExchangeRecord: CredentialExchangeRecord,
    val formatService: List<CredentialFormatService<*>>,
    val comment: String?,
    val goal: String?,
    val goalCode: String?,
    val credentialFormat: Map<String, JsonElement>?
)