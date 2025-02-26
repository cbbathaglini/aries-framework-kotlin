package org.hyperledger.ariesframework.credentialsv2.formats

import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentialsv2.models.Format

/**
 * Extracts the `CredentialFormat` type from a `CredentialFormatService`.
 *
 * Example:
 * ```
 * typealias TheCredentialFormat = ExtractCredentialFormat<IndyCredentialFormatService>
 * ```
 */
typealias ExtractCredentialFormat<T> = T

/**
 * Extracts an array of `CredentialFormat` types from an array of `CredentialFormatService` types.
 */
typealias ExtractCredentialFormats<CFs> = List<ExtractCredentialFormat<CFs>>

/**
 * Base return type for all methods that create an attachment format.
 */
data class CredentialFormatCreateReturn(
    val format: Format,
    val attachment: Attachment,
    val appendAttachments: List<Attachment>? = null
)

/**
 * Base return type for all credential process methods.
 */
data class CredentialFormatProcessOptions(
    val attachment: Attachment,
    val credentialRecord: CredentialExchangeRecord
)

/**
 * Options for processing a credential.
 */
data class CredentialFormatProcessCredentialOptions(
    val attachment: Attachment,
    val credentialRecord: CredentialExchangeRecord,
    val offerAttachment: Attachment,
    val requestAttachment: Attachment,
    val requestAppendAttachments: List<Attachment>? = null
)

/**
 * Options for creating a credential proposal.
 */
data class CredentialFormatCreateProposalOptions<CF>(
    val credentialRecord: CredentialExchangeRecord,
    val credentialFormats: Map<String, Any?>, // Equivalent to CredentialFormatPayload<[CF], 'createProposal'>
    val attachmentId: String? = null
)

/**
 * Options for accepting a credential proposal.
 */
data class CredentialFormatAcceptProposalOptions<CF>(
    val credentialRecord: CredentialExchangeRecord,
    val credentialFormats: Map<String, Any?>? = null, // Equivalent to CredentialFormatPayload<[CF], 'acceptProposal'>
    val attachmentId: String? = null,
    val proposalAttachment: Attachment
)

/**
 * Return type for creating a credential proposal.
 */
data class CredentialFormatCreateProposalReturn(
    val format: Format,
    val attachment: Attachment,
    val appendAttachments: List<Attachment>? = null,
    val previewAttributes: List<CredentialPreviewAttributeOptionsV2>? = null
)

/**
 * Options for creating a credential offer.
 */
data class CredentialFormatCreateOfferOptions<CF>(
    val credentialRecord: CredentialExchangeRecord,
    val credentialFormats: Map<String, Any?>, // Equivalent to CredentialFormatPayload<[CF], 'createOffer'>
    val attachmentId: String? = null
)

/**
 * Options for accepting a credential offer.
 */
data class CredentialFormatAcceptOfferOptions<CF>(
    val credentialRecord: CredentialExchangeRecord,
    val credentialFormats: Map<String, Any?>? = null, // Equivalent to CredentialFormatPayload<[CF], 'acceptOffer'>
    val attachmentId: String? = null,
    val offerAttachment: Attachment
)

/**
 * Return type for creating a credential offer.
 */
data class CredentialFormatCreateOfferReturn(
    val format: Format,
    val attachment: Attachment,
    val appendAttachments: List<Attachment>? = null,
    val previewAttributes: List<CredentialPreviewAttributeOptionsV2>? = null
)

/**
 * Options for creating a credential request.
 */
data class CredentialFormatCreateRequestOptions<CF>(
    val credentialRecord: CredentialExchangeRecord,
    val credentialFormats: Map<String, Any?> // Equivalent to CredentialFormatPayload<[CF], 'createRequest'>
)

/**
 * Options for accepting a credential request.
 */
data class CredentialFormatAcceptRequestOptions<CF>(
    val credentialRecord: CredentialExchangeRecord,
    val credentialFormats: Map<String, Any?>? = null, // Equivalent to CredentialFormatPayload<[CF], 'acceptRequest'>
    val attachmentId: String? = null,
    val offerAttachment: Attachment? = null,
    val requestAttachment: Attachment,
    val requestAppendAttachments: List<Attachment>? = null
)

// Auto accept method options

data class CredentialFormatAutoRespondProposalOptions(
    val credentialRecord: CredentialExchangeRecord,
    val proposalAttachment: Attachment,
    val offerAttachment: Attachment
)

data class CredentialFormatAutoRespondOfferOptions(
    val credentialRecord: CredentialExchangeRecord,
    val proposalAttachment: Attachment,
    val offerAttachment: Attachment
)

data class CredentialFormatAutoRespondRequestOptions(
    val credentialRecord: CredentialExchangeRecord,
    val proposalAttachment: Attachment? = null,
    val offerAttachment: Attachment,
    val requestAttachment: Attachment
)

data class CredentialFormatAutoRespondCredentialOptions(
    val credentialRecord: CredentialExchangeRecord,
    val proposalAttachment: Attachment? = null,
    val offerAttachment: Attachment? = null,
    val requestAttachment: Attachment,
    val credentialAttachment: Attachment
)