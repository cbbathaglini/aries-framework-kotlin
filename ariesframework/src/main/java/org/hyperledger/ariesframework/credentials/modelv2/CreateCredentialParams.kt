package org.hyperledger.ariesframework.credentials.modelv2

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord

@Serializable
data class CreateCredentialParams (
    val credentialRecord: CredentialExchangeRecord,
    val formatServices: List<CredentialFormatService<*>>,
    val comment: String? = null,
    val goal: String? = null,
    val goalCode: String? = null,
    val credentialFormats: Map<String, JsonElement>? = emptyMap()
)