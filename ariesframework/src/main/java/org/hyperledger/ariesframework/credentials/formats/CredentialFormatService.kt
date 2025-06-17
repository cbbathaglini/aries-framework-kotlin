package org.hyperledger.ariesframework.credentials.formats

import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.models.CredentialFormatCreateOfferReturn
import org.hyperledger.ariesframework.credentials.modelv2.CredentialFormatCreateReturn
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord

interface CredentialFormatService<CF : CredentialFormat>
{

    val formatKey: String
    val credentialRecordType: String

    // Proposal methods
    suspend fun createProposal(
        agentContext: AgentContext,
        options: CredentialFormatCreateProposalOptions<CF>
    ): CredentialFormatCreateProposalReturn

    suspend fun processProposal(
        attachment: Attachment,
        credentialRecord: CredentialExchangeRecord
    )

    suspend fun acceptProposal(
        credentialFormats: Map<String, JsonElement?>,
        credentialRecord: CredentialExchangeRecord,
        proposalAttachments: Attachment
    ): CredentialFormatCreateOfferReturn

    // Offer methods
    suspend fun createOffer(
        credentialFormats: Map<String, JsonElement?>,
        credentialExchangeRecord: CredentialExchangeRecord
    ): CredentialFormatCreateOfferReturn

    suspend fun processOffer(
        attachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord
    )

    suspend fun acceptOffer(
        attachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord,
        credentialFormats: Map<String, JsonElement>?
    ): CredentialFormatCreateReturn

    // Request methods
    suspend fun createRequest(
        credentialFormats: Map<String, JsonElement>?,
        credentialExchangeRecord: CredentialExchangeRecord
    ): CredentialFormatCreateReturn

    suspend fun processRequest(
        attachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord
    )

    suspend fun acceptRequest(
        requestAttachment: Attachment,
        offerAttachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord,
        credentialFormats: Map<String, JsonElement>?,
        requestAppendAttachments: Attachment
    ): CredentialFormatCreateReturn

    // Credential methods
    suspend fun processCredential(
        attachment: Attachment,
        offerAttachment: Attachment,
        requestAttachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord,
        requestAppendAttachments: Attachment
    )

    // Auto accept methods
    suspend fun shouldAutoRespondToProposal(
        credentialRecord: CredentialExchangeRecord,
        offerAttachment: Attachment,
        proposalAttachment: Attachment
    ): Boolean

    suspend fun shouldAutoRespondToOffer(
        credentialRecord: CredentialExchangeRecord,
        offerAttachment: Attachment,
        proposalAttachment: Attachment
    ): Boolean

    suspend fun shouldAutoRespondToRequest(
        credentialRecord: CredentialExchangeRecord,
        offerAttachment: Attachment,
        requestAttachment: Attachment,
        proposalAttachment: Attachment
    ): Boolean

    suspend fun shouldAutoRespondToCredential(
        credentialRecord: CredentialExchangeRecord,
        offerAttachment: Attachment,
        issueAttachment: Attachment,
        requestAttachment: Attachment,
        proposalAttachment: Attachment
    ): Boolean

    suspend fun deleteCredentialById(
        agentContext: AgentContext,
        credentialId: String
    )

    fun supportsFormat(formatIdentifier: String): Boolean
}