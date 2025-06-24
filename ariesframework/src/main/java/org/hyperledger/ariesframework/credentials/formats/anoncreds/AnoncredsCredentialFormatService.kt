package org.hyperledger.ariesframework.credentials.formats.anoncreds

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.AttachmentData
import org.hyperledger.ariesframework.anoncreds.exception.ProblemReportError
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredential
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialOffer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequestMetadata
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.anoncreds.model.CreateCredentialRequestOptions
import org.hyperledger.ariesframework.anoncreds.model.FetchSchemaReturn
import org.hyperledger.ariesframework.anoncreds.model.StoreCredential
import org.hyperledger.ariesframework.anoncreds.model.StoreCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.issuer.AnonCredsCredentialValue
import org.hyperledger.ariesframework.anoncreds.model.issuer.AnonCredsCredentialValues
import org.hyperledger.ariesframework.anoncreds.model.issuer.CreateCredentialOptions
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsRevocationRegistryState
import org.hyperledger.ariesframework.anoncreds.utils.AnonCredsEncoder
import org.hyperledger.ariesframework.anoncreds.utils.AnonCredsObjects
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.formats.LinkedAttachment
import org.hyperledger.ariesframework.credentials.models.CredentialFormatCreateOfferReturn
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute
import org.hyperledger.ariesframework.credentials.modelv2.CredentialLinkedAttachmentsResult
import org.hyperledger.ariesframework.credentials.modelv2.problemreport.CredentialProblemReportReason
import org.hyperledger.ariesframework.credentials.modelv2.returns.CredentialFormatCreateProposalReturn
import org.hyperledger.ariesframework.credentials.modelv2.returns.CredentialFormatCreateReturn
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.repository.CredentialRecordBinding
import org.hyperledger.ariesframework.credentials.utils.Functions
import org.hyperledger.ariesframework.credentials.utils.JsonEncoder
import org.hyperledger.ariesframework.credentials.v2.models.Format
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.util.ConvertFromAny
import org.slf4j.LoggerFactory
import java.util.Date


class AnoncredsCredentialFormatService(
    override val formatKey: String,
    override val credentialRecordType: String,
    val agent: Agent
) : CredentialFormatService<AnoncredsCredentialFormat> {

    private val logger = LoggerFactory.getLogger(AnoncredsCredentialFormatService::class.java)

    companion object {
        const val ANONCREDS_CREDENTIAL_OFFER = "anoncreds/credential-offer@v1.0"
        const val ANONCREDS_CREDENTIAL_REQUEST = "anoncreds/credential-request@v1.0"
        const val ANONCREDS_CREDENTIAL_FILTER = "anoncreds/credential-filter@v1.0"
        const val ANONCREDS_CREDENTIAL = "anoncreds/credential@v1.0"
    }

    /**
     * Create a {@link AttachmentFormats} object dependent on the message type.
     *
     * @param options The object containing all the options for the proposed credential
     * @returns object containing associated attachment, format and optionally the credential preview
     *
     */
    override suspend fun createProposal(
        credentialFormats: Map<String, JsonElement>?,
        credentialExchangeRecord: CredentialExchangeRecord
    ): CredentialFormatCreateProposalReturn {

        val format = Format(format = ANONCREDS_CREDENTIAL_FILTER)
        val anoncredsFormat = getAnonCredsFormatGeneric<AnonCredsProposeCredentialFormat>(credentialFormats)


        val proposal = AnonCredsCredentialProposal(
            schemaIssuerDid = anoncredsFormat.schemaIssuerDid,
            schemaIssuerId = anoncredsFormat.schemaIssuerId,
            schemaId = anoncredsFormat.schemaId,
            schemaName = anoncredsFormat.schemaName,
            schemaVersion = anoncredsFormat.schemaVersion,
            credentialDefinitionId = anoncredsFormat.credentialDefinitionId,
            issuerDid = anoncredsFormat.issuerDid,
            issuerId = anoncredsFormat.issuerId
        )


        try{
            MessageValidator.validateSync(proposal)
        } catch (error:Exception) {
            throw CredoError("Invalid proposal supplied: ${proposal} in AnonCredsFormatService")
        }

        val jsonElement = Json.encodeToJsonElement(AnonCredsCredentialProposal.serializer(), proposal)
        val attachment = getFormatData(jsonElement, format.attachId)

        val credentialLinkedAttachmentsResult = getCredentialLinkedAttachments(
            anoncredsFormat.attributes,
            anoncredsFormat.linkedAttachments
        )

        credentialExchangeRecord.metadata.set(
            MetadataKeys.AnonCredsCredentialMetadataKey,
            AnonCredsCredentialMetadata(
                schemaId = proposal.schemaId,
                credentialDefinitionId = proposal.credentialDefinitionId
            )
        )

        return CredentialFormatCreateProposalReturn(
            format = format,
            attachment = attachment,
            previewAttribute = credentialLinkedAttachmentsResult.previewAttributes
        )
    }

    override suspend fun processProposal(
        attachment: Attachment,
        credentialRecord: CredentialExchangeRecord
    ) {
        val proposal = parseAttachmentData<AnonCredsCredentialProposal>(attachment)
        logger.info("Processed proposal ${proposal}")
    }

    override suspend fun acceptProposal(
        attachmentId: String?,
        credentialFormats: Map<String, JsonElement>?,
        credentialRecord: CredentialExchangeRecord,
        proposalAttachments: Attachment
    ): CredentialFormatCreateOfferReturn {

        val anoncredsFormat = getAnonCredsFormatGeneric<AnoncredsCredentialFormat>(credentialFormats)

        val proposalJson = proposalAttachments.getDataAsJson()//<AnonCredsCredentialProposalFormat>()
        val proposalFormat = Json.decodeFromString<AnonCredsCredentialProposalFormat>(proposalJson)
        val credentialDefinitionId = anoncredsFormat.credentialDefinitionId ?: proposalFormat.credDefId

        val attributes = anoncredsFormat.attributes ?: credentialRecord.credentialAttributes

        if (credentialDefinitionId == null) {
            throw CredoError("No credential definition id in proposal or provided as input to accept proposal method.")
        }

        if (attributes == null) {
            throw CredoError("No attributes in proposal or provided as input to accept proposal method.")
        }

        val createAnoncredsOffer = CreateAnoncredsOffer(
            credentialExchangeRecord= credentialRecord,
            attachmentId= attachmentId,
            attributes= attributes,
            credentialDefinitionId= credentialDefinitionId,
            revocationRegistryDefinitionId= anoncredsFormat.revocationRegistryDefinitionId,
            revocationRegistryIndex= anoncredsFormat.revocationRegistryIndex,
            linkedAttachments= anoncredsFormat.linkedAttachments,
        )
        val credentialFormatCreateOfferReturn = createAnonCredsOffer(createAnoncredsOffer)

        return CredentialFormatCreateOfferReturn(
            format= credentialFormatCreateOfferReturn.format,
            attachment = credentialFormatCreateOfferReturn.attachment,
            previewAttributes = credentialFormatCreateOfferReturn.previewAttributes
        )
    }

    override suspend fun createOffer(
        credentialFormats: Map<String, JsonElement>?,
        credentialExchangeRecord: CredentialExchangeRecord,
        attachmentId: String?
    ): CredentialFormatCreateOfferReturn {

        val anoncredsFormat = getAnonCredsFormatGeneric<AnoncredsCredentialFormat>(credentialFormats)

        val createAnoncredsOffer = CreateAnoncredsOffer(
            credentialExchangeRecord= credentialExchangeRecord,
            attachmentId= attachmentId,
            attributes= anoncredsFormat.attributes,
            credentialDefinitionId= anoncredsFormat.credentialDefinitionId,
            revocationRegistryDefinitionId= anoncredsFormat.revocationRegistryDefinitionId,
            revocationRegistryIndex= anoncredsFormat.revocationRegistryIndex,
            linkedAttachments= anoncredsFormat.linkedAttachments,
        )
        val credentialFormatCreateOfferReturn = createAnonCredsOffer(createAnoncredsOffer)

        return CredentialFormatCreateOfferReturn(
            format= credentialFormatCreateOfferReturn.format,
            attachment = credentialFormatCreateOfferReturn.attachment,
            previewAttributes = credentialFormatCreateOfferReturn.previewAttributes
        )

    }

    override suspend fun processOffer(
        attachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord
    ) {
        logger.debug("Processing anoncreds credential offer for credential record ${credentialExchangeRecord.id}")

        val offerJson = attachment.getDataAsJson()
        val offerJsonElement = Json.parseToJsonElement(offerJson)
        val offer = Json.decodeFromJsonElement<AnonCredsCredentialOffer>(offerJsonElement)

        if (offer.schemaId.isBlank() || offer.credDefId.isBlank()) {
            throw ProblemReportError(
                message = "Invalid credential offer",
                problemCode = CredentialProblemReportReason.IssuanceAbandoned.name
            )
        }

    }

    override suspend fun acceptOffer(
        attachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord,
        credentialFormats: Map<String, JsonElement>?,
        attachmentId: String?
    ): CredentialFormatCreateReturn {

        val offer = parseAttachmentData<AnonCredsCredentialOffer>(attachment)
        val anoncredsFormat = getAnonCredsFormatGeneric<AnoncredsCredentialFormat>(credentialFormats)

        val fetchedCredentialDefinitionResult =  AnonCredsObjects.fetchCredentialDefinition(agent,offer.credDefId)

        val createCredentialRequestOptions = CreateCredentialRequestOptions(
            credentialOffer = offer,
            credentialDefinition = fetchedCredentialDefinitionResult.credentialDefinition,
            linkSecretId = anoncredsFormat.linkSecretId
        )
        val createCredentialRequestReturn = agent.anonCredsHolderService.createCredentialRequest(createCredentialRequestOptions)

        val anonCredsCredentialRequestMetadata = createCredentialRequestReturn.credentialRequestMetadata

        credentialExchangeRecord.metadata.set(
            MetadataKeys.AnonCredsCredentialRequestMetadataKey,
            AnonCredsCredentialRequestMetadata(
                link_secret_name = anonCredsCredentialRequestMetadata.link_secret_name,
                link_secret_blinding_data = anonCredsCredentialRequestMetadata.link_secret_blinding_data,
                nonce = anonCredsCredentialRequestMetadata.nonce
            )
        )

        credentialExchangeRecord.metadata.set(
            MetadataKeys.AnonCredsCredentialMetadataKey,
            AnonCredsCredentialMetadata(
                schemaId = offer.schemaId,
                credentialDefinitionId = offer.credDefId
            )
        )

        val format = Format(
                attachId = attachmentId ?: BaseRecord.generateId(),
                format= ANONCREDS_CREDENTIAL_REQUEST,
        )

        val attachment = getFormatData(createCredentialRequestReturn, format.attachId)

        return CredentialFormatCreateReturn(
            attachment = attachment,
            format = format
        )
    }

    override suspend fun createRequest(
        credentialFormats: Map<String, JsonElement>?,
        credentialExchangeRecord: CredentialExchangeRecord
    ): CredentialFormatCreateReturn {
        throw CredoError("Starting from a request is not supported for anoncreds credentials")
    }

    /**
     * We don't have any models to validate an anoncreds request object, for now this method does nothing
     */
    override suspend fun processRequest(
        attachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord
    ) {
        // not needed for anoncreds
    }


    override suspend fun acceptRequest(
        requestAttachment: Attachment,
        offerAttachment: Attachment?,
        credentialExchangeRecord: CredentialExchangeRecord,
        credentialFormats: Map<String, JsonElement>?,
        requestAppendAttachments: List<Attachment>?,
        attachmentId: String?
    ): CredentialFormatCreateReturn {

        val credentialAttributes = credentialExchangeRecord.credentialAttributes
        if (credentialAttributes == null) {
            throw CredoError("Missing required credential attribute values on credential record with id ${credentialExchangeRecord.id}")
        }

        if (offerAttachment == null) throw CredoError("Missing offer attachments in accept request")
        val credentialOffer = parseAttachmentData<AnonCredsCredentialOffer>(offerAttachment) ?: throw CredoError("Missing anoncreds credential offer in createCredential")
        val credentialRequest = parseAttachmentData<AnonCredsCredentialRequest>(requestAttachment) ?: throw CredoError("Missing anoncreds credential request in createCredential")

        val anonCredsCredentialDefinitionRecord = agent.anoncredsCredentialDefinitionRepository.getByCredentialDefinitionId(credentialRequest.credDefId)
        val credentialDefinition = anonCredsCredentialDefinitionRecord.credentialDefinition
        val revocation = credentialDefinition.value.revocation

        var revocationRegistryDefinitionId : String? = null
        var revocationRegistryIndex : Int? = null
        var revocationStatusList : AnonCredsRevocationStatusList? = null

        if (revocation != null) {

            val metadata = credentialExchangeRecord.metadata

            val credentialMetadata = metadata[MetadataKeys.AnonCredsCredentialMetadataKey] as? AnonCredsCredentialMetadata

            revocationRegistryDefinitionId = credentialMetadata?.revocationRegistryId
            revocationRegistryIndex = credentialMetadata?.credentialRevocationId?.toIntOrNull()

            if (revocationRegistryDefinitionId.isNullOrBlank() || revocationRegistryIndex == null) {
                throw CredoError("Revocation registry definition id and revocation index are mandatory to issue AnonCreds revocable credentials")
            }

            val revocationRegistryDefinitionPrivateRecord = agent.anonCredsRevocationRegistryDefinitionPrivateRepository
                .getByRevocationRegistryDefinitionId(revocationRegistryDefinitionId)

            if (revocationRegistryDefinitionPrivateRecord.state != AnonCredsRevocationRegistryState.Active) {
                throw CredoError(
                    "Revocation registry $revocationRegistryDefinitionId is in ${revocationRegistryDefinitionPrivateRecord.state} state"
                )
            }

            revocationStatusList = AnonCredsObjects.fetchRevocationStatusList(
                agent,
                revocationRegistryDefinitionId,
                dateToTimestamp(Date())
            )
        }

        val createCredentialOptions = CreateCredentialOptions(
            credentialOffer = credentialOffer, // AQUI NAO PODE SE RNULO
            credentialRequest = credentialRequest,
            credentialValues = convertAttributesToCredentialValues(credentialAttributes),
            revocationRegistryDefinitionId = revocationRegistryDefinitionId,
            revocationStatusList = revocationStatusList,
            revocationRegistryIndex = revocationRegistryIndex
        )
        val createCredentialReturn =  agent.anonCredsIssuerService.createCredential(createCredentialOptions)
        val credentialRevocationId = createCredentialReturn.credentialRevocationId
        val credential = createCredentialReturn.credential

        if (credential.revRegId != null) {
            credentialExchangeRecord.addMetadata(
                MetadataKeys.AnonCredsCredentialMetadataKey,
                AnonCredsCredentialMetadata(
                    revocationRegistryId = revocationRegistryDefinitionId,
                    credentialRevocationId = createCredentialReturn.credentialRevocationId
                )
            )

            if (revocationRegistryDefinitionId != null && credentialRevocationId != null) {
                val tags: Tags = mapOf(
                    "anonCredsRevocationRegistryId" to revocationRegistryDefinitionId,
                    "anonCredsCredentialRevocationId" to credentialRevocationId
                )
                credentialExchangeRecord.setTags(tags)
            }
        }

        val format = Format(
                attachId = attachmentId ?: BaseRecord.generateId(),
                format= ANONCREDS_CREDENTIAL,
        )

        val attachment = getFormatData(credential, format.attachId)

        return CredentialFormatCreateReturn(
            attachment = attachment,
            format = format
        )
    }

    override suspend fun processCredential(
        attachment: Attachment,
        offerAttachment: Attachment,
        requestAttachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord,
        requestAppendAttachments: List<Attachment>?
    ) {

        val credentialRequestMetadata = credentialExchangeRecord.metadata.get(MetadataKeys.AnonCredsCredentialRequestMetadataKey)

        if (credentialRequestMetadata == null) {
            throw CredoError("Missing required request metadata for credential exchange with thread id with id ${credentialExchangeRecord.id}")
        }

        if (credentialExchangeRecord.credentialAttributes == null) {
            throw CredoError("Missing credential attributes on credential record. Unable to check credential attributes")
        }

        val anonCredsCredential = parseAttachmentData<AnonCredsCredential>(attachment)

        val credentialDefinitionResult = AnonCredsObjects.fetchCredentialDefinition(agent,anonCredsCredential.credDefId)
        val fetchSchemaReturn =  fetchSchema(anonCredsCredential.schemaId)
        val revocationRegistryResult = if (anonCredsCredential.revRegId != null) AnonCredsObjects.fetchRevocationRegistryDefinition(agent,anonCredsCredential.revRegId)  else null

        val recordCredentialValues = if(credentialExchangeRecord.credentialAttributes != null) convertAttributesToCredentialValues(credentialExchangeRecord.credentialAttributes!!) else
            throw CredoError("Missing credential attributes on credential record. Unable to check credential attributes")

        assertCredentialValuesMatch(anonCredsCredential.values, recordCredentialValues)

        val storeCredential = StoreCredentialOptions(
            credential = anonCredsCredential,
            credentialRequestMetadata = ConvertFromAny.convertAnyToSerializable<AnonCredsCredentialRequestMetadata>(credentialRequestMetadata),
            credentialDefinition = credentialDefinitionResult.credentialDefinition,
            schema = fetchSchemaReturn.schema,
            credentialDefinitionId = credentialDefinitionResult.credentialDefinitionId,
            credentialId = BaseRecord.generateId(),
            revocationRegistry = revocationRegistryResult?.revocationRegistryDefinition
        )
        val storeCredentialOptions = StoreCredential.getStoreCredentialOptions(
            options =  storeCredential,
            indyNamespace = fetchSchemaReturn.indyNamespace
        )

        val credentialId = agent.anonCredsHolderService.storeCredential(storeCredentialOptions)


        if (anonCredsCredential.revRegId != null) {
            val credential = agent.anonCredsHolderService.getCredential(credentialId)
            val revocationRegistryId = credential.revocationRegistryId
            val credentialRevocationId = credential.credentialRevocationId

            credentialExchangeRecord.addMetadata(
                MetadataKeys.AnonCredsCredentialMetadataKey,
                AnonCredsCredentialMetadata(
                    credentialRevocationId = credentialRevocationId,
                    revocationRegistryId = revocationRegistryId
                )
            )

            if (revocationRegistryId != null && credentialRevocationId != null) {
                val tags: Tags = mapOf(
                    "anonCredsRevocationRegistryId" to revocationRegistryId,
                    "anonCredsCredentialRevocationId" to credentialRevocationId
                )
                credentialExchangeRecord.setTags(tags)
            }

        }

        val credentialBiding = CredentialRecordBinding(
            credentialRecordType= this.credentialRecordType,
            credentialRecordId= credentialId,
        )
        credentialExchangeRecord.credentials.add(credentialBiding)
    }

    override suspend fun shouldAutoRespondToProposal(
        credentialRecord: CredentialExchangeRecord,
        offerAttachment: Attachment,
        proposalAttachment: Attachment
    ): Boolean {
        val credentialOffer = parseAttachmentData<AnonCredsCredentialOffer>(offerAttachment) ?: throw CredoError("Missing anoncreds credential offer in shouldAutoRespondToProposal")
        val proposal = parseAttachmentData<AnonCredsCredentialProposalFormat>(proposalAttachment) ?: throw CredoError("Missing anoncreds credential proposal in shouldAutoRespondToProposal")
        return  proposal.credDefId == credentialOffer.credDefId
    }

    override suspend fun shouldAutoRespondToOffer(
        credentialRecord: CredentialExchangeRecord,
        offerAttachment: Attachment,
        proposalAttachment: Attachment
    ): Boolean {
        val credentialOffer = parseAttachmentData<AnonCredsCredentialOffer>(offerAttachment) ?: throw CredoError("Missing anoncreds credential offer in shouldAutoRespondToProposal")
        val proposal = parseAttachmentData<AnonCredsCredentialProposalFormat>(proposalAttachment) ?: throw CredoError("Missing anoncreds credential proposal in shouldAutoRespondToProposal")
        return  proposal.credDefId == credentialOffer.credDefId
    }

    override suspend fun shouldAutoRespondToRequest(
        credentialRecord: CredentialExchangeRecord,
        offerAttachment: Attachment,
        requestAttachment: Attachment,
        proposalAttachment: Attachment
    ): Boolean {
        val credentialOffer = parseAttachmentData<AnonCredsCredentialOffer>(offerAttachment) ?: throw CredoError("Missing anoncreds credential offer in shouldAutoRespondToProposal")
        val requestOffer = parseAttachmentData<AnonCredsCredentialRequest>(requestAttachment) ?: throw CredoError("Missing anoncreds credential request in shouldAutoRespondToProposal")
        return  requestOffer.credDefId == credentialOffer.credDefId
    }

    override suspend fun shouldAutoRespondToCredential(
        credentialRecord: CredentialExchangeRecord,
        offerAttachment: Attachment,
        issueAttachment: Attachment,
        requestAttachment: Attachment,
        proposalAttachment: Attachment
    ): Boolean {
        val credential = parseAttachmentData<AnonCredsCredential>(offerAttachment) ?: throw CredoError("Missing anoncreds credential offer in shouldAutoRespondToProposal")
        val requestOffer = parseAttachmentData<AnonCredsCredentialRequest>(requestAttachment) ?: throw CredoError("Missing anoncreds credential request in shouldAutoRespondToProposal")

        if ((credential.credDefId != requestOffer.credDefId) || (credentialRecord.credentialAttributes == null)) return false

        val attributeValues = convertAttributesToCredentialValues(credentialRecord.credentialAttributes!!)
        return checkCredentialValuesMatch(attributeValues, credential.values)
    }

    override suspend fun deleteCredentialById(credentialId: String) {
        agent.anonCredsHolderService.deleteCredential(credentialId)
    }

    override fun supportsFormat(formatIdentifier: String): Boolean {
        val supportedFormats = listOf(
            ANONCREDS_CREDENTIAL_REQUEST,
            ANONCREDS_CREDENTIAL_OFFER,
            ANONCREDS_CREDENTIAL_FILTER,
            ANONCREDS_CREDENTIAL
        )

        return formatIdentifier in supportedFormats
    }

    fun assertAttributesMatch(schema: AnonCredsSchema, attributes: List<CredentialPreviewAttribute>) {
        val schemaAttributes = schema.attrNames
        val credAttributes = attributes.map { it.name }

        val difference = (credAttributes - schemaAttributes) + (schemaAttributes - credAttributes)

        if (difference.isNotEmpty()) {
            throw CredoError(
                "The credential preview attributes do not match the schema attributes (difference is: $difference, needs: $schemaAttributes)"
            )
        }
    }

    /**
     * Returns an object of type {@link Attachment} for use in credential exchange messages.
     * It looks up the correct format identifier and encodes the data as a base64 attachment.
     *
     * @param data The data to include in the attach object
     * @param id the attach id from the formats component of the message
     */
    fun getFormatData(data: Any, id: String): Attachment {
        val base64 = JsonEncoder.toBase64(data)

        return Attachment(
            id = id,
            mimetype = "application/json",
            data = AttachmentData(base64 = base64)
        )
    }

    fun getCredentialLinkedAttachments(
        attributes: List<CredentialPreviewAttribute>? = null,
        linkedAttachments: List<LinkedAttachment>? = null
    ): CredentialLinkedAttachmentsResult {
        if (linkedAttachments == null && attributes == null) {
            return CredentialLinkedAttachmentsResult()
        }

        var previewAttributesResult = attributes ?: emptyList()
        var attachments: List<Attachment>? = null

        if (linkedAttachments != null) {
            // transforma linkedAttachments e anexa no preview
            previewAttributesResult = Functions.createAndLinkAttachmentsToPreview(linkedAttachments, previewAttributesResult)
            attachments = linkedAttachments.map { it.attachment }
        }

        return CredentialLinkedAttachmentsResult(
            attachments = attachments,
            previewAttributes = previewAttributesResult
        )
    }

    fun assertCredentialValuesMatch(
        firstValues: Map<String, AnonCredsCredentialValue>,
        secondValues: Map<String, AnonCredsCredentialValue>
    ) {
        val firstKeys = firstValues.keys
        val secondKeys = secondValues.keys

        if (firstKeys.size != secondKeys.size) {
            throw IllegalArgumentException(
                "Number of values in first entry (${firstKeys.size}) does not match number of values in second entry (${secondKeys.size})"
            )
        }

        for (key in firstKeys) {
            val firstValue = firstValues[key]
            val secondValue = secondValues[key]

            if (secondValue == null) {
                throw IllegalArgumentException("Second cred values object has no value for key '$key'")
            }

            if (firstValue?.encoded != secondValue.encoded) {
                throw IllegalArgumentException("Encoded credential values for key '$key' do not match")
            }

            if (firstValue.raw != secondValue.raw) {
                throw IllegalArgumentException("Raw credential values for key '$key' do not match")
            }
        }
    }


    private suspend fun createAnonCredsOffer(createAnoncredsOffer: CreateAnoncredsOffer) : CredentialFormatCreateOfferReturn{

        val credentialExchangeRecord = createAnoncredsOffer.credentialExchangeRecord
        val revocationRegistryDefinitionId = createAnoncredsOffer.revocationRegistryDefinitionId
        val credentialDefinitionId = createAnoncredsOffer.credentialDefinitionId
        val revocationRegistryIndex = createAnoncredsOffer.revocationRegistryIndex

        val format = Format(
                attachId = createAnoncredsOffer.attachmentId ?: BaseRecord.generateId(),
                format =  ANONCREDS_CREDENTIAL_OFFER,
        )

        val anoncredsCredentialOffer =  agent.anonCredsIssuerService.createCredentialOffer(credentialDefinitionId)

        val ( attachments, previewAttributes ) = this.getCredentialLinkedAttachments(
            attributes = createAnoncredsOffer.attributes,
            linkedAttachments = createAnoncredsOffer.linkedAttachments)

        if (previewAttributes == null) {
            throw CredoError("Missing required preview attributes for anoncreds offer")
        }

        assertPreviewAttributesMatchSchemaAttributes(anoncredsCredentialOffer, previewAttributes)

        val anonCredsCredentialDefinitionRecord = agent.anoncredsCredentialDefinitionRepository.getByCredentialDefinitionId(credentialDefinitionId)
        val credentialDefinition = anonCredsCredentialDefinitionRecord.credentialDefinition.value

        if (credentialDefinition.revocation != null) {
            if (revocationRegistryDefinitionId == null || revocationRegistryIndex == null) {
                throw CredoError("AnonCreds revocable credentials require revocationRegistryDefinitionId and revocationRegistryIndex")
            }

            val tags: Tags = mapOf(
                "anonCredsRevocationRegistryId" to revocationRegistryDefinitionId,
                "anonCredsCredentialRevocationId" to revocationRegistryIndex.toString()
            )
            credentialExchangeRecord.setTags(tags)
        }


        credentialExchangeRecord.metadata.set(
            MetadataKeys.AnonCredsCredentialMetadataKey,
            AnonCredsCredentialMetadata(
                schemaId = anoncredsCredentialOffer.schemaId,
                credentialDefinitionId = anoncredsCredentialOffer.credDefId,
                credentialRevocationId = revocationRegistryIndex.toString(),
                revocationRegistryId = revocationRegistryDefinitionId
            )
        )

        val attachment = getFormatData(anoncredsCredentialOffer, format.attachId)

        return CredentialFormatCreateOfferReturn(
            format = format,
            attachment = attachment,
            previewAttributes = previewAttributes
        )
    }

    private suspend fun assertPreviewAttributesMatchSchemaAttributes(anoncredsCredentialOffer : AnonCredsCredentialOffer, previewAttributes:  List<CredentialPreviewAttribute>){
        val fetchSchemaReturn = fetchSchema(anoncredsCredentialOffer.schemaId)
        assertAttributesMatch(fetchSchemaReturn.schema, previewAttributes)
    }

    private suspend fun fetchSchema(schemaId: String) : FetchSchemaReturn {

        val result = agent.anonCredsRegistryService
            .getRegistryForIdentifier(schemaId)
            .getSchema(schemaId)

        if (result.schema == null) {
            throw CredoError("Schema not found for id $schemaId: ${result.resolutionMetadata.message}")
        }

        return FetchSchemaReturn(
            schema = result.schema,
            schemaId = result.schemaId,
            indyNamespace = result.schemaMetadata["didIndyNamespace"] as? String
        )

    }

    fun checkCredentialValuesMatch(
        firstValues: Map<String, AnonCredsCredentialValue>,
        secondValues: Map<String, AnonCredsCredentialValue>
    ): Boolean {
        return try {
            assertCredentialValuesMatch(firstValues, secondValues)
            true
        } catch (e: Exception) {
            false
        }
    }

    inline fun <reified T> getAnonCredsFormatGeneric(
        credentialFormats: Map<String, JsonElement>?
    ): T {
        val json = Json {
            ignoreUnknownKeys = true
        }

        val anonCredsJson = credentialFormats?.get("anoncreds")
            ?: throw IllegalArgumentException("Missing 'anoncreds' credential format")

        return json.decodeFromJsonElement(anonCredsJson)
    }

    inline fun <reified T> parseAttachmentData(attachment: Attachment): T {
        val jsonString = attachment.getDataAsJson()
        val jsonElement = Json.parseToJsonElement(jsonString)
        return Json.decodeFromJsonElement(jsonElement)
    }

    fun dateToTimestamp(date: Date): Long = date.time / 1000

    fun convertAttributesToCredentialValues(
        attributes: List<CredentialPreviewAttribute>
    ): AnonCredsCredentialValues{
        return attributes.associate { attribute ->
            attribute.name to AnonCredsCredentialValue(
                raw = attribute.value,
                encoded = AnonCredsEncoder.encodeCredentialValue(attribute.value)
            )
        }
    }

}
