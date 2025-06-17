package org.hyperledger.ariesframework.credentials.modelv2

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord

@Serializable
data class CreateCredentialProblemReportOptions (
    val credentialExchangeRecord: CredentialExchangeRecord,
    val description: String
)