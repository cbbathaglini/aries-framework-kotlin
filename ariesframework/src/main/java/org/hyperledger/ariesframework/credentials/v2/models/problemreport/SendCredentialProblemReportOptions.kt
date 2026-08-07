package org.hyperledger.ariesframework.credentials.v2.models.problemreport

import kotlinx.serialization.Serializable

@Serializable
data class SendCredentialProblemReportOptions(
    val credentialRecordId: String,
    val description: String,
)
