package org.hyperledger.ariesframework.credentials.v2.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.CredentialsConstants
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

@Serializable
data class CreateProposalOptionsV2(
    val connection: ConnectionRecord,
    val autoAcceptCredential: AutoAcceptCredential? = null,
    val credentialRecord: CredentialExchangeRecord,
    val credentialFormats: Map<String, JsonElement>,
    val proposalAttachments: List<Attachment>,
    val comment: String? = null,
    val goal: String? = null,
    val goalCode: String? = null,
    val credentialPreview: CredentialPreviewV2? = null,
    val protocolVersion: String,
    val credentialDefinitionId: String? = null,
    val issuerDid: String? = null,
    val threadId: String,
    val parentThreadId: String? = null,
) {


}
