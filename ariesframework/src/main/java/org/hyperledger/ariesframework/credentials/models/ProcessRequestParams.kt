package org.hyperledger.ariesframework.credentials.models

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.messages.RequestCredentialMessageV2

@Serializable
data class ProcessRequestParams(
    val credentialExchangeRecord: CredentialExchangeRecord,
    val message: RequestCredentialMessageV2,
    val formatService: List<CredentialFormatService<*>>,
)
