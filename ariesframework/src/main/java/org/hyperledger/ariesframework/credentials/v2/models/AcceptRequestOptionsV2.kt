package org.hyperledger.ariesframework.credentials.v2.models

import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

class AcceptRequestOptionsV2(
    val credentialRecordId: String,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
)