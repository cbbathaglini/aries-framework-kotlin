package org.hyperledger.ariesframework.credentials.modelv2.problemreport

import kotlinx.serialization.SerialName

enum class CredentialProblemReportReason {
    @SerialName("issuance-abandoned")
    IssuanceAbandoned
}