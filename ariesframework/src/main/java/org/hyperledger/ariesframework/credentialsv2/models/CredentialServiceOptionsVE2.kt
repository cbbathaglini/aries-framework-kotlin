package org.hyperledger.ariesframework.credentialsv2.models

import kotlinx.serialization.SerialName
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.models.AutoAcceptCredential

class CreateProposalOptionsV2(
    val connection: ConnectionRecord,
    val credentialPreview: CredentialPreviewV2? = null,
    val schemaIssuerDid: String? = null,
    val schemaId: String? = null,
    val schemaName: String? = null,
    val schemaVersion: String? = null,
    val credentialDefinitionId: String? = null,
    val issuerDid: String? = null,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
    val goalCode: String? = null,
    val goal: String? = null,
    val formats : List<Formats> = ArrayList(),
    val proposalAttachments : List<Attachment> = ArrayList(),
    val attachments : List<Attachment> = ArrayList()
)

class CreateOfferOptionsV2(
    val connection: ConnectionRecord? = null,
    val credentialDefinitionId: String,
    val attributes: List<CredentialPreviewAttribute>,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
)

class AcceptOfferOptions(
    val credentialRecordId: String,
    val holderDid: String? = null,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
)

class AcceptRequestOptions(
    val credentialRecordId: String,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val comment: String? = null,
)

class AcceptCredentialOptions(
    val credentialRecordId: String,
)
