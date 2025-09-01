package org.hyperledger.ariesframework.credentials.v2.models

import kotlinx.serialization.Serializable

@Serializable
data class DeclineCredentialOfferOptions(
    val sendProblemReport: Boolean? = false,
    val problemReportDescription: String? = null,
)
