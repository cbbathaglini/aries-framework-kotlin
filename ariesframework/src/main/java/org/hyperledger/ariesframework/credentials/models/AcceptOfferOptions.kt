package org.hyperledger.ariesframework.credentials.models

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

@Serializable
data class AcceptOfferOptions(
    val credentialRecordId: String,
    val holderDid: String? = null,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
)
