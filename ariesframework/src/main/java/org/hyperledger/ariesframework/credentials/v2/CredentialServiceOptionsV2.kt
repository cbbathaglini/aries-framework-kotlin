package org.hyperledger.ariesframework.credentials.v2

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.v1.models.CredentialPreview
import org.hyperledger.ariesframework.credentials.v1.models.CredentialPreviewAttribute

//class CredentialServiceOptionsV2 (
//    val connection: ConnectionRecord,
//    val credentialPreview: CredentialPreview? = null,
//    val schemaIssuerDid: String? = null,
//    val schemaId: String? = null,
//    val schemaName: String? = null,
//    val schemaVersion: String? = null,
//    val credentialDefinitionId: String? = null,
//    val issuerDid: String? = null,
//    val autoAcceptCredential: AutoAcceptCredential? = null,
//    val comment: String? = null,
//)
//
//
//
//@Serializable
//data class AcceptOfferOptionsV2(
//    val credentialRecordId: String,
//    val holderDid: String? = null,
//    val autoAcceptCredential: AutoAcceptCredential? = null,
//    val comment: String? = null,
//)
//
//class AcceptRequestOptionsV2(
//    val credentialRecordId: String,
//    val autoAcceptCredential: AutoAcceptCredential? = null,
//    val comment: String? = null,
//)
//
//class AcceptCredentialOptionsV2(
//    val credentialRecordId: String,
//)
