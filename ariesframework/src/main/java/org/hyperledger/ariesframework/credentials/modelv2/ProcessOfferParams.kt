package org.hyperledger.ariesframework.credentials.modelv2

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2

@Serializable
data class ProcessOfferParams (
    val credentialExchangeRecord: CredentialExchangeRecord,
    val message: OfferCredentialMessageV2,
    val formatService: List<CredentialFormatService<*>>
)