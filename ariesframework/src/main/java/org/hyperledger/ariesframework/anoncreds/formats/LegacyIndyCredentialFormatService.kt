package org.hyperledger.ariesframework.anoncreds.formats

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.anoncreds.exception.ProblemReportError
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsCredentialProposalFormat
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsProposeCredentialFormat
import org.hyperledger.ariesframework.anoncreds.formats.legacyindy.CreateIndyOfferOptions
import org.hyperledger.ariesframework.anoncreds.formats.legacyindy.LegacyIndyCredentialFormat
import org.hyperledger.ariesframework.anoncreds.formats.legacyindy.LegacyIndyCredentialProposalFormat
import org.hyperledger.ariesframework.anoncreds.formats.model.CredentialFormatCreateOfferReturn
import org.hyperledger.ariesframework.anoncreds.formats.model.CredentialFormatCreateProposalReturn
import org.hyperledger.ariesframework.anoncreds.formats.model.CredentialFormatCreateReturn
import org.hyperledger.ariesframework.anoncreds.formats.utils.Credential
import org.hyperledger.ariesframework.anoncreds.formats.utils.FormatDataUtil
import org.hyperledger.ariesframework.anoncreds.formats.utils.FormatGeneric
import org.hyperledger.ariesframework.anoncreds.formats.utils.ProverDid
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredential
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialOffer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequestMetadata
import org.hyperledger.ariesframework.anoncreds.model.CreateCredentialRequestOptions
import org.hyperledger.ariesframework.anoncreds.model.RevocationRegistryInfo
import org.hyperledger.ariesframework.anoncreds.model.StoreCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateCredentialRequestReturn
import org.hyperledger.ariesframework.anoncreds.model.issuer.CreateCredentialOptions
import org.hyperledger.ariesframework.anoncreds.utils.AnonCredsObjects
import org.hyperledger.ariesframework.anoncreds.utils.Indyidentifiers
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.formats.anoncreds.AnonCredsCredentialMetadata
import org.hyperledger.ariesframework.credentials.formats.anoncreds.AnonCredsCredentialProposal
import org.hyperledger.ariesframework.credentials.formats.anoncreds.MessageValidator
import org.hyperledger.ariesframework.credentials.formats.anoncreds.MetadataKeys
import org.hyperledger.ariesframework.credentials.models.problemreport.CredentialProblemReportReason
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.repository.CredentialRecordBinding
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.models.Format
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.util.ConvertFromAny
import org.slf4j.LoggerFactory

class LegacyIndyCredentialFormatService(
    override val formatKey: String = "indy",
    override val credentialRecordType: String = "w3c",
    val agent: Agent,
) : CredentialFormatService<LegacyIndyCredentialFormat> {

    private val logger = LoggerFactory.getLogger(LegacyIndyCredentialFormatService::class.java)

    companion object {
        const val INDY_CRED_ABSTRACT = "hlindy/cred-abstract@v2.0"
        const val INDY_CRED_REQUEST = "hlindy/cred-req@v2.0"
        const val INDY_CRED_FILTER = "hlindy/cred-filter@v2.0"
        const val INDY_CRED = "hlindy/cred@v2.0"
    }

    override suspend fun createProposal(
        credentialFormats: Map<String, JsonElement>?,
        credentialExchangeRecord: CredentialExchangeRecord,
    ): CredentialFormatCreateProposalReturn {
        val format = Format(format = INDY_CRED_FILTER)
        val indyFormat = FormatGeneric.getLegacyIndyFormatGeneric<AnonCredsProposeCredentialFormat>(credentialFormats)

        val indyCredentialProposal = AnonCredsCredentialProposal(
            schemaIssuerDid = indyFormat.schemaIssuerDid,
            schemaIssuerId = indyFormat.schemaIssuerId,
            schemaId = indyFormat.schemaId,
            schemaName = indyFormat.schemaName,
            schemaVersion = indyFormat.schemaVersion,
            credentialDefinitionId = indyFormat.credentialDefinitionId,
            issuerDid = indyFormat.issuerDid,
            issuerId = indyFormat.issuerId,
        )

        try {
            MessageValidator.validateSync(indyCredentialProposal)
        } catch (error: Exception) {
            throw CredoError("Invalid proposal supplied: $indyCredentialProposal in Indy Format Service")
        }

        val jsonElement = Json.encodeToJsonElement(
            serializer<AnonCredsCredentialProposal>(),
            indyCredentialProposal,
        )
        val attachment = FormatDataUtil.getFormatData(jsonElement, format.attachId)

        val credentialLinkedAttachmentsResult = FormatDataUtil.getCredentialLinkedAttachments(
            attributes = indyFormat.attributes,
            linkedAttachments = indyFormat.linkedAttachments,
        )

        credentialExchangeRecord.metadata.set(
            MetadataKeys.AnonCredsCredentialMetadataKey,
            Json.encodeToJsonElement(
                AnonCredsCredentialMetadata(
                    schemaId = indyCredentialProposal.schemaId,
                    credentialDefinitionId = indyCredentialProposal.credentialDefinitionId,
                ),
            ),
        )

        return CredentialFormatCreateProposalReturn(
            format = format,
            attachment = attachment,
            previewAttribute = credentialLinkedAttachmentsResult.previewAttributes,
        )
    }

    override suspend fun processProposal(
        attachment: Attachment,
        credentialRecord: CredentialExchangeRecord,
    ) {
        val proposal = FormatDataUtil.parseAttachmentData<AnonCredsCredentialProposal>(attachment)
        logger.info("Processed proposal $proposal")
    }

    override suspend fun acceptProposal(
        attachmentId: String?,
        credentialFormats: Map<String, JsonElement>?,
        credentialRecord: CredentialExchangeRecord,
        proposalAttachments: Attachment,
    ): CredentialFormatCreateOfferReturn {
        val indyFormat = FormatGeneric.getLegacyIndyFormatGeneric<LegacyIndyCredentialProposalFormat>(credentialFormats)

        val proposalJson = proposalAttachments.getDataAsJson() // <AnonCredsCredentialProposalFormat>()
        val proposalFormat = Json.decodeFromString<AnonCredsCredentialProposalFormat>(proposalJson)
        val credentialDefinitionId = indyFormat.credentialDefinitionId ?: proposalFormat.credDefId

        val attributes = indyFormat.attributes ?: credentialRecord.credentialAttributes

        if (credentialDefinitionId == null) {
            throw CredoError("No credential definition id in proposal or provided as input to accept proposal method.")
        }

        if (!Indyidentifiers.isUnqualifiedCredentialDefinitionId(credentialDefinitionId)) {
            throw CredoError("$credentialDefinitionId is not a valid legacy indy credential definition id")
        }

        if (attributes == null) {
            throw CredoError("No attributes in proposal or provided as input to accept proposal method.")
        }

        val createIndyOfferOptions = CreateIndyOfferOptions(
            credentialExchangeRecord = credentialRecord,
            attachmentId = attachmentId,
            attributes = attributes,
            credentialDefinitionId = credentialDefinitionId,
            linkedAttachments = indyFormat.linkedAttachments,
        )

        return createIndyOffer(createIndyOfferOptions)
    }

    override suspend fun createOffer(
        credentialFormats: Map<String, JsonElement>?,
        credentialExchangeRecord: CredentialExchangeRecord,
        attachmentId: String?,
    ): CredentialFormatCreateOfferReturn {
        val indyFormat = FormatGeneric.getLegacyIndyFormatGeneric<LegacyIndyCredentialFormat>(credentialFormats)

        val createIndyOfferOptions = CreateIndyOfferOptions(
            credentialExchangeRecord = credentialExchangeRecord,
            attachmentId = attachmentId,
            attributes = indyFormat.attributes,
            credentialDefinitionId = indyFormat.credentialDefinitionId,
            linkedAttachments = indyFormat.linkedAttachments,
        )

        return createIndyOffer(createIndyOfferOptions)
    }

    override suspend fun processOffer(
        attachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord,
    ) {
        logger.info("Processing indy credential offer for credential record ${credentialExchangeRecord.id}")

//        val offerJson = attachment.getDataAsJson()
//        val offerJsonElement = Json.parseToJsonElement(offerJson)
//        val offer = Json.decodeFromJsonElement<AnonCredsCredentialOffer>(offerJsonElement)

        val offer = AnonCredsCredentialOffer.fromAttachment(attachment)
        logger.info("credential offer: $offer")

        if (!Indyidentifiers.isUnqualifiedSchemaId(offer.schemaId) || !Indyidentifiers.isUnqualifiedCredentialDefinitionId(offer.credDefId)) {
            throw ProblemReportError(
                message = "Invalid credential offer",
                problemCode = CredentialProblemReportReason.IssuanceAbandoned.name,
            )
        }
    }

    override suspend fun acceptOffer(
        attachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord,
        credentialFormats: List<Format>?,
        attachmentId: String?,
        offerCredentialMessageV2: OfferCredentialMessageV2,
    ): CredentialFormatCreateReturn {
        logger.info("attachment: $attachment")
        val credentialOffer = AnonCredsCredentialOffer.fromAttachment(attachment)
        logger.info("credential offer: $credentialOffer")

        if (!Indyidentifiers.isUnqualifiedCredentialDefinitionId(credentialOffer.credDefId)) {
            throw CredoError("${credentialOffer.credDefId} is not a valid legacy indy credential definition id")
        }

        // val fetchedCredentialDefinitionResult  = AnonCredsObjects.fetchCredentialDefinition(agent, credentialOffer.credDefId)

        val credentialDefinition =
            agent.ledgerService.getCredentialDefinition(credentialOffer.credDefId)
        logger.info("credentialDefinition : $credentialDefinition")

        val createCredentialRequestOptions = CreateCredentialRequestOptions(
            credentialOffer = credentialOffer,
            credentialDefinition = credentialDefinition,
            linkSecretId = agent.wallet.linkSecretId!!,
            useLegacyProverDid = true,
        )
        logger.info("createCredentialRequestOptions : $createCredentialRequestOptions")

        val createCredentialRequestReturn: CreateCredentialRequestReturn = agent.anonCredsHolderService.createCredentialRequest(createCredentialRequestOptions)
        logger.info("createCredentialRequestReturn: $createCredentialRequestReturn")

        val anoncredsCredentialRequest = createCredentialRequestReturn.credentialRequest
        val anoncredsCredentialRequestMetadata = createCredentialRequestReturn.credentialRequestMetadata

        if (anoncredsCredentialRequest.proverDid == null) {
            anoncredsCredentialRequest.proverDid = ProverDid.generateLegacyProverDidLikeString()
        }

        credentialExchangeRecord.metadata.set(
            MetadataKeys.AnonCredsCredentialRequestMetadataKey,
            Json.encodeToJsonElement(anoncredsCredentialRequestMetadata),
        )

        credentialExchangeRecord.metadata.set(
            MetadataKeys.AnonCredsCredentialMetadataKey,
            Json.encodeToJsonElement(
                AnonCredsCredentialMetadata(
                    schemaId = credentialOffer.schemaId,
                    credentialDefinitionId = credentialOffer.credDefId,
                ),
            ),
        )

        val format = Format(
            attachId = attachmentId ?: BaseRecord.generateId(),
            format = INDY_CRED_REQUEST,
        )

        return CredentialFormatCreateReturn(
            attachment = FormatDataUtil.getFormatData(anoncredsCredentialRequest, format.attachId),
            format = format,
        )
    }

    override suspend fun createRequest(
        credentialFormats: List<Format>?,
        credentialExchangeRecord: CredentialExchangeRecord,
    ): CredentialFormatCreateReturn {
        throw CredoError("Starting from a request is not supported for indy credentials")
    }

    override suspend fun processRequest(
        attachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord,
    ) {
        // not needed for indy
    }

    override suspend fun acceptRequest(
        requestAttachment: Attachment,
        offerAttachment: Attachment?,
        credentialExchangeRecord: CredentialExchangeRecord,
        credentialFormats: Map<String, JsonElement>?,
        requestAppendAttachments: List<Attachment>?,
        attachmentId: String?,
    ): CredentialFormatCreateReturn {
        val credentialAttributes = credentialExchangeRecord.credentialAttributes
        if (credentialAttributes == null) {
            throw CredoError("Missing required credential attribute values on credential record with id ${credentialExchangeRecord.id}")
        }

        if (offerAttachment == null) throw CredoError("Missing offer attachments in accept request")
        val credentialOffer = FormatDataUtil.parseAttachmentData<AnonCredsCredentialOffer>(offerAttachment) ?: throw CredoError("Missing indy credential offer in createCredential")
        val credentialRequest = FormatDataUtil.parseAttachmentData<AnonCredsCredentialRequest>(requestAttachment) ?: throw CredoError("Missing indy credential request in createCredential")

        val createCredentialOptions = CreateCredentialOptions(
            credentialOffer = credentialOffer,
            credentialRequest = credentialRequest,
            credentialValues = Credential.convertAttributesToCredentialValues(credentialAttributes),
        )
        val createCredentialReturn = agent.anonCredsIssuerService.createCredential(createCredentialOptions)
        val credential = createCredentialReturn.credential

        if (credential.revRegId != null) {
            val credentialRevocationId = createCredentialReturn.credentialRevocationId

            credentialExchangeRecord.addMetadata(
                MetadataKeys.AnonCredsCredentialMetadataKey,
                Json.encodeToJsonElement(
                    AnonCredsCredentialMetadata(
                        revocationRegistryId = credential.revRegId,
                        credentialRevocationId = createCredentialReturn.credentialRevocationId,
                    ),
                ),
            )

            if (credentialRevocationId != null) {
                val tags: Tags = mapOf(
                    "anonCredsRevocationRegistryId" to credential.revRegId,
                    "anonCredsCredentialRevocationId" to credentialRevocationId,
                )
                credentialExchangeRecord.setTags(tags)
            }
        }

        val format = Format(
            attachId = attachmentId ?: BaseRecord.generateId(),
            format = INDY_CRED,
        )

        val attachment = FormatDataUtil.getFormatData(credential, format.attachId)
        return CredentialFormatCreateReturn(
            attachment = attachment,
            format = format,
        )
    }

    override suspend fun processCredential(
        attachment: Attachment,
        offerAttachment: Attachment,
        requestAttachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord,
        requestAppendAttachments: List<Attachment>?,
    ) {
        val credentialRequestMetadata = credentialExchangeRecord.metadata.get(MetadataKeys.AnonCredsCredentialRequestMetadataKey)

        if (credentialRequestMetadata == null) {
            throw CredoError("Missing required request metadata for credential exchange with thread id with id ${credentialExchangeRecord.id}")
        }

        if (credentialExchangeRecord.credentialAttributes == null) {
            throw CredoError("Missing credential attributes on credential record. Unable to check credential attributes")
        }

        val anonCredsCredential = FormatDataUtil.parseAttachmentData<AnonCredsCredential>(attachment)

        val credentialDefinitionResult = AnonCredsObjects.fetchCredentialDefinition(agent, anonCredsCredential.credDefId)
        val fetchSchemaReturn = FormatDataUtil.fetchSchema(agent, anonCredsCredential.schemaId)
        val revocationRegistryResult = if (anonCredsCredential.revRegId != null) AnonCredsObjects.fetchRevocationRegistryDefinition(agent, anonCredsCredential.revRegId) else null

        val recordCredentialValues = if (credentialExchangeRecord.credentialAttributes != null) {
            Credential.convertAttributesToCredentialValues(credentialExchangeRecord.credentialAttributes!!)
        } else {
            throw CredoError("Missing credential attributes on credential record. Unable to check credential attributes")
        }

        Credential.assertCredentialValuesMatch(anonCredsCredential.values, recordCredentialValues)

        var revocationRegistryInfo: RevocationRegistryInfo? = null

        if (revocationRegistryResult != null && revocationRegistryResult.revocationRegistryDefinition != null) {
            revocationRegistryInfo = RevocationRegistryInfo(
                id = revocationRegistryResult.revocationRegistryDefinitionId,
                definition = revocationRegistryResult.revocationRegistryDefinition,
            )
        }

        val storeCredentialOptions = StoreCredentialOptions(
            credential = anonCredsCredential,
            credentialRequestMetadata = ConvertFromAny.convertAnyToSerializable<AnonCredsCredentialRequestMetadata>(credentialRequestMetadata),
            credentialDefinition = credentialDefinitionResult.credentialDefinition,
            schema = fetchSchemaReturn.schema,
            credentialDefinitionId = credentialDefinitionResult.credentialDefinitionId,
            credentialId = BaseRecord.generateId(),
            revocationRegistry = revocationRegistryInfo,
        )

        val credentialId = agent.anonCredsHolderService.storeCredential(storeCredentialOptions)

        if (anonCredsCredential.revRegId != null) {
            val credential = agent.anonCredsHolderService.getCredential(credentialId)

            val revocationRegistryId = credential.revocationRegistryId
            val credentialRevocationId = credential.credentialRevocationId

            credentialExchangeRecord.addMetadata(
                MetadataKeys.AnonCredsCredentialMetadataKey,
                Json.encodeToJsonElement(
                    AnonCredsCredentialMetadata(
                        credentialRevocationId = credentialRevocationId,
                        revocationRegistryId = revocationRegistryId,
                    ),
                ),
            )

            if (revocationRegistryId != null && credentialRevocationId != null) {
                val tags: Tags = mapOf(
                    "anonCredsRevocationRegistryId" to revocationRegistryId,
                    "anonCredsCredentialRevocationId" to credentialRevocationId,
                )
                credentialExchangeRecord.setTags(tags)
            }

            val credentialRecordBinding = CredentialRecordBinding(
                credentialRecordType = this.credentialRecordType,
                credentialRecordId = credentialId,
            )
            credentialExchangeRecord.credentials.add(credentialRecordBinding)
        }
    }

    override suspend fun shouldAutoRespondToProposal(
        credentialRecord: CredentialExchangeRecord,
        offerAttachment: Attachment,
        proposalAttachment: Attachment,
    ): Boolean {
        val credentialOffer = FormatDataUtil.parseAttachmentData<AnonCredsCredentialOffer>(offerAttachment) ?: throw CredoError("Missing anoncreds credential offer in shouldAutoRespondToProposal")
        val proposal = FormatDataUtil.parseAttachmentData<LegacyIndyCredentialProposalFormat>(proposalAttachment) ?: throw CredoError("Missing anoncreds credential proposal in shouldAutoRespondToProposal")
        return proposal.credentialDefinitionId == credentialOffer.credDefId
    }

    override suspend fun shouldAutoRespondToOffer(
        credentialRecord: CredentialExchangeRecord,
        offerAttachment: Attachment,
        proposalAttachment: Attachment,
    ): Boolean {
        val credentialOffer = FormatDataUtil.parseAttachmentData<AnonCredsCredentialOffer>(offerAttachment) ?: throw CredoError("Missing anoncreds credential offer in shouldAutoRespondToProposal")
        val proposal = FormatDataUtil.parseAttachmentData<LegacyIndyCredentialProposalFormat>(proposalAttachment) ?: throw CredoError("Missing anoncreds credential proposal in shouldAutoRespondToProposal")
        return proposal.credentialDefinitionId == credentialOffer.credDefId
    }

    override suspend fun shouldAutoRespondToRequest(
        credentialRecord: CredentialExchangeRecord,
        offerAttachment: Attachment,
        requestAttachment: Attachment,
        proposalAttachment: Attachment,
    ): Boolean {
        val credentialOffer = FormatDataUtil.parseAttachmentData<AnonCredsCredentialOffer>(offerAttachment) ?: throw CredoError("Missing anoncreds credential offer in shouldAutoRespondToProposal")
        val requestOffer = FormatDataUtil.parseAttachmentData<AnonCredsCredentialRequest>(requestAttachment) ?: throw CredoError("Missing anoncreds credential request in shouldAutoRespondToProposal")
        return requestOffer.credDefId == credentialOffer.credDefId
    }

    override suspend fun shouldAutoRespondToCredential(
        credentialRecord: CredentialExchangeRecord,
        offerAttachment: Attachment,
        issueAttachment: Attachment,
        requestAttachment: Attachment,
        proposalAttachment: Attachment,
    ): Boolean {
        val credential = FormatDataUtil.parseAttachmentData<AnonCredsCredential>(offerAttachment) ?: throw CredoError("Missing anoncreds credential offer in shouldAutoRespondToProposal")
        val requestOffer = FormatDataUtil.parseAttachmentData<AnonCredsCredentialRequest>(requestAttachment) ?: throw CredoError("Missing anoncreds credential request in shouldAutoRespondToProposal")

        if ((credential.credDefId != requestOffer.credDefId) || (credentialRecord.credentialAttributes == null)) return false

        val attributeValues = Credential.convertAttributesToCredentialValues(credentialRecord.credentialAttributes!!)
        return Credential.checkCredentialValuesMatch(attributeValues, credential.values)
    }

    override suspend fun deleteCredentialById(credentialId: String) {
        agent.anonCredsHolderService.deleteCredential(credentialId)
    }

    override fun supportsFormat(formatIdentifier: String): Boolean {
        val supportedFormats = listOf(
            INDY_CRED_ABSTRACT,
            INDY_CRED_REQUEST,
            INDY_CRED_FILTER,
            INDY_CRED,
        )
        return formatIdentifier in supportedFormats
    }

    private suspend fun createIndyOffer(options: CreateIndyOfferOptions): CredentialFormatCreateOfferReturn {
        val attachmentId = options.attachmentId
        val credentialDefinitionId = options.credentialDefinitionId
        val attributes = options.attributes
        val linkedAttachments = options.linkedAttachments
        val credentialExchangeRecord = options.credentialExchangeRecord

        val format = Format(
            attachId = attachmentId ?: BaseRecord.generateId(),
            format = INDY_CRED_ABSTRACT,
        )

        val offer = agent.anonCredsIssuerService.createCredentialOffer(credentialDefinitionId)

        val (attachments, previewAttributes) = FormatDataUtil.getCredentialLinkedAttachments(
            attributes = attributes,
            linkedAttachments = linkedAttachments,
        )

        if (previewAttributes == null) {
            throw CredoError("Missing required preview attributes for anoncreds offer")
        }

        FormatDataUtil.assertPreviewAttributesMatchSchemaAttributes(agent, offer, previewAttributes)

        credentialExchangeRecord.metadata.set(
            MetadataKeys.AnonCredsCredentialMetadataKey,
            Json.encodeToJsonElement(
                AnonCredsCredentialMetadata(
                    schemaId = offer.schemaId,
                    credentialDefinitionId = offer.credDefId,
                ),
            ),
        )

        val attachment = FormatDataUtil.getFormatData(offer, format.attachId)

        return CredentialFormatCreateOfferReturn(
            format = format,
            attachment = attachment,
            previewAttributes = previewAttributes,
        )
    }
}
