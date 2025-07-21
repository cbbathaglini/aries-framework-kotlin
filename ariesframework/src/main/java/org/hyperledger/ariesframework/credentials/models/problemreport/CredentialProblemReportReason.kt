package org.hyperledger.ariesframework.credentials.models.problemreport

import kotlinx.serialization.SerialName

enum class CredentialProblemReportReason {
    @SerialName("issuance-abandoned")
    IssuanceAbandoned
}