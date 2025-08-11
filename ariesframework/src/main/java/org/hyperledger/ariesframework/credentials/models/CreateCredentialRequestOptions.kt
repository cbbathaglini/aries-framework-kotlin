package org.hyperledger.ariesframework.credentials.models

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.v2.models.Format

@Serializable
data class CreateCredentialRequestOptions (
    val credentialFormats : List<Format>,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
    val goal: String? = null,
    val goalCode: String? = null,
    val connectionRecord: ConnectionRecord
)