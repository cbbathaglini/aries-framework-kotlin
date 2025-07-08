package org.hyperledger.ariesframework.credentials.formats

import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.anoncreds.formats.model.CredentialFormatCreateOfferReturn
import org.hyperledger.ariesframework.anoncreds.formats.model.CredentialFormatCreateProposalReturn
import org.hyperledger.ariesframework.anoncreds.formats.model.CredentialFormatCreateReturn
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.models.Format

interface CredentialFormatService<CF : CredentialFormat>
{
    val formatKey: String
    val credentialRecordType: String

    // Proposal methods
    suspend fun createProposal(
        credentialFormats: Map<String, JsonElement>? = emptyMap(),
        credentialExchangeRecord: CredentialExchangeRecord
    ): CredentialFormatCreateProposalReturn

    suspend fun processProposal(
        attachment: Attachment,
        credentialRecord: CredentialExchangeRecord
    )

    suspend fun acceptProposal(
        attachmentId: String? = null,
        credentialFormats: Map<String, JsonElement>? = emptyMap(),
        credentialRecord: CredentialExchangeRecord,
        proposalAttachments: Attachment
    ): CredentialFormatCreateOfferReturn

    // Offer methods
    suspend fun createOffer(
        credentialFormats: Map<String, JsonElement>? = emptyMap(),
        credentialExchangeRecord: CredentialExchangeRecord,
        attachmentId: String? = null
    ): CredentialFormatCreateOfferReturn

    suspend fun processOffer(
        attachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord
    )

    suspend fun acceptOffer(
        attachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord,
        //credentialFormats: Map<String, JsonElement>? = emptyMap(),
        credentialFormats: List<Format>? = emptyList(),
        attachmentId: String? = null,
        offerCredentialMessageV2: OfferCredentialMessageV2
    ): CredentialFormatCreateReturn

    suspend fun createRequest(
        credentialFormats: List<Format>? = emptyList(),
        credentialExchangeRecord: CredentialExchangeRecord
    ): CredentialFormatCreateReturn

    suspend fun processRequest(
        attachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord
    )

    suspend fun acceptRequest(
        requestAttachment: Attachment,
        offerAttachment: Attachment? = null,
        credentialExchangeRecord: CredentialExchangeRecord,
        credentialFormats: Map<String, JsonElement>? = emptyMap(),
        requestAppendAttachments: List<Attachment>? = emptyList(),
        attachmentId: String? = null
    ): CredentialFormatCreateReturn

    suspend fun processCredential(
        attachment: Attachment,
        offerAttachment: Attachment,
        requestAttachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord,
        requestAppendAttachments: List<Attachment>? = emptyList()
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
        credentialId: String
    )

    fun supportsFormat(formatIdentifier: String): Boolean
}