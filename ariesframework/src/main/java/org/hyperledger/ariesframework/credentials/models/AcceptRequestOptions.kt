package org.hyperledger.ariesframework.credentials.models

import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

class AcceptRequestOptions(
    val credentialRecordId: String,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
)