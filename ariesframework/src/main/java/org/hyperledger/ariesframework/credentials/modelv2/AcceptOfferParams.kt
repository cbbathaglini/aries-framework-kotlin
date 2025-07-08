package org.hyperledger.ariesframework.credentials.modelv2

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.models.Format

@Serializable
data class AcceptOfferParams (
    val credentialRecord: CredentialExchangeRecord,
    val formatServices: List<CredentialFormatService<*>>,
    val comment: String? = null,
    val goal: String? =  null,
    val goalCode: String? = null,
    //val credentialFormats: Map<String, JsonElement>?,
    val credentialFormats: List<Format>? = emptyList()
)
