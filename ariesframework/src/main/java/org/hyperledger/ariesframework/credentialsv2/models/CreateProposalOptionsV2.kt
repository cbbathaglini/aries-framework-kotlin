package org.hyperledger.ariesframework.credentialsv2.models

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.models.CredentialPreview

@Serializable
data class CreateProposalOptionsV2(
    val connection: ConnectionRecord,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val credentialFormats: Format = null,
    val comment: String? = null,
    val goal: String? = null,
    val goalCode: String? = null,
    val credentialPreview: CredentialPreviewV2? = null,

    val credentialDefinitionId: String? = null,
    val issuerDid: String? = null,


)