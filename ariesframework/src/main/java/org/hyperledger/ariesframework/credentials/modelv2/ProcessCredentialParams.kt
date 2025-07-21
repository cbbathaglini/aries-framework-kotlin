package org.hyperledger.ariesframework.credentials.modelv2

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.messages.IssueCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.RequestCredentialMessageV2

@Serializable
data class ProcessCredentialParams (
    val credentialExchangeRecord: CredentialExchangeRecord,
    val formatService: List<CredentialFormatService<*>>,
    val requestCredentialMessageV2: RequestCredentialMessageV2,
    val message: IssueCredentialMessageV2
)