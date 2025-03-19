package org.hyperledger.ariesframework.credentials.v2.models

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

@Serializable
data class AcceptOfferOptionsV2(
    val credentialRecordId: String,
    val holderDid: String? = null,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
)
