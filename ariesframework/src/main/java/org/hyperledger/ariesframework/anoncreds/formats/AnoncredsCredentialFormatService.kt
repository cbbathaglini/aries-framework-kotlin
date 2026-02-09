package org.hyperledger.ariesframework.anoncreds.formats

import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.CredentialOffer
import anoncreds_uniffi.CredentialRequestTuple
import anoncreds_uniffi.Prover
import anoncreds_uniffi.RevocationRegistryDefinition
import anoncreds_uniffi.Verifier
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.anoncreds.exception.ProblemReportError
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsCredentialProposalFormat
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsProposeCredentialFormat
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnoncredsCredentialFormat
import org.hyperledger.ariesframework.anoncreds.formats.model.CredentialFormatCreateOfferReturn
import org.hyperledger.ariesframework.anoncreds.formats.model.CredentialFormatCreateProposalReturn
import org.hyperledger.ariesframework.anoncreds.formats.model.CredentialFormatCreateReturn
import org.hyperledger.ariesframework.anoncreds.formats.utils.Credential
import org.hyperledger.ariesframework.anoncreds.formats.utils.FormatDataUtil
import org.hyperledger.ariesframework.anoncreds.formats.utils.FormatGeneric
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredential
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialOffer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequestMetadata
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsLinkSecretBlindingData
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList
import org.hyperledger.ariesframework.anoncreds.model.FetchIntermediateRevocationRegistryDefinitionResult
import org.hyperledger.ariesframework.anoncreds.model.FetchSchemaReturn
import org.hyperledger.ariesframework.anoncreds.model.RevocationRegistryInfo
import org.hyperledger.ariesframework.anoncreds.model.StoreCredential
import org.hyperledger.ariesframework.anoncreds.model.StoreCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.issuer.CreateCredentialOptions
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsRevocationRegistryState
import org.hyperledger.ariesframework.anoncreds.utils.AnonCredsObjects
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.formats.anoncreds.AnonCredsCredentialMetadata
import org.hyperledger.ariesframework.credentials.formats.anoncreds.AnonCredsCredentialProposal
import org.hyperledger.ariesframework.credentials.formats.anoncreds.CreateAnoncredsOffer
import org.hyperledger.ariesframework.credentials.formats.anoncreds.MessageValidator
import org.hyperledger.ariesframework.credentials.formats.anoncreds.MetadataKeys
import org.hyperledger.ariesframework.credentials.models.problemreport.CredentialProblemReportReason
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.repository.CredentialRecordBinding
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.models.Format
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.util.Base64Operations
import org.hyperledger.ariesframework.util.LogUtil
import org.hyperledger.ariesframework.util.PrintLongLine
import org.slf4j.LoggerFactory
import java.util.Date

class AnoncredsCredentialFormatService(
    override val formatKey: String = "anoncreds",
    override val credentialRecordType: String = "w3c",
    val agent: Agent,
) : CredentialFormatService<AnoncredsCredentialFormat> {

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
        credentialExchangeRecord: CredentialExchangeRecord,
    ): CredentialFormatCreateProposalReturn {
        LogUtil.info(this) { "creating proposal" }
        val format = Format(format = ANONCREDS_CREDENTIAL_FILTER)
        //PrintLongLine.print("credentialFormats------- $credentialFormats")
        val anoncredsFormat = FormatGeneric.getAnonCredsFormatGeneric<AnonCredsProposeCredentialFormat>(credentialFormats)

        val proposal = AnonCredsCredentialProposal(
            schemaIssuerDid = anoncredsFormat.schemaIssuerDid,
            schemaIssuerId = anoncredsFormat.schemaIssuerId,
            schemaId = anoncredsFormat.schemaId,
            schemaName = anoncredsFormat.schemaName,
            schemaVersion = anoncredsFormat.schemaVersion,
            credentialDefinitionId = anoncredsFormat.credentialDefinitionId,
            issuerDid = anoncredsFormat.issuerDid,
            issuerId = anoncredsFormat.issuerId,
        )

        try {
            MessageValidator.validateSync(proposal)
        } catch (error: Exception) {
            throw CredoError("Invalid proposal supplied: $proposal in AnonCredsFormatService")
        }

        val jsonElement = Json.encodeToJsonElement(
            serializer<AnonCredsCredentialProposal>(),
            proposal,
        )
        val attachment = FormatDataUtil.getFormatData(jsonElement, format.attachId)

        val credentialLinkedAttachmentsResult = FormatDataUtil.getCredentialLinkedAttachments(
            anoncredsFormat.attributes,
            anoncredsFormat.linkedAttachments,
        )

        credentialExchangeRecord.metadata.set(
            MetadataKeys.AnonCredsCredentialMetadataKey,
            Json.encodeToJsonElement(
                AnonCredsCredentialMetadata(
                    schemaId = proposal.schemaId,
                    credentialDefinitionId = proposal.credentialDefinitionId,
                ),
            ),
        )

        LogUtil.info(this) { "proposal created" }
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
        LogUtil.info(this) { "processing proposal" }
        val proposal = FormatDataUtil.parseAttachmentData<AnonCredsCredentialProposal>(attachment)
        LogUtil.info(this) { "proposal processed" }
    }

    override suspend fun acceptProposal(
        attachmentId: String?,
        credentialFormats: Map<String, JsonElement>?,
        credentialRecord: CredentialExchangeRecord,
        proposalAttachments: Attachment,
    ): CredentialFormatCreateOfferReturn {
        LogUtil.info(this) { "accepting proposal" }
        //PrintLongLine.print("credentialFormats------- $credentialFormats")
        val anoncredsFormat = FormatGeneric.getAnonCredsFormatGeneric<AnoncredsCredentialFormat>(credentialFormats)

        val proposalJson = proposalAttachments.getDataAsJson() // <AnonCredsCredentialProposalFormat>()
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
            credentialExchangeRecord = credentialRecord,
            attachmentId = attachmentId,
            attributes = attributes,
            credentialDefinitionId = credentialDefinitionId,
            revocationRegistryDefinitionId = anoncredsFormat.revocationRegistryDefinitionId,
            revocationRegistryIndex = anoncredsFormat.revocationRegistryIndex,
            linkedAttachments = anoncredsFormat.linkedAttachments,
        )
        val credentialFormatCreateOfferReturn = createAnonCredsOffer(createAnoncredsOffer)

        return CredentialFormatCreateOfferReturn(
            format = credentialFormatCreateOfferReturn.format,
            attachment = credentialFormatCreateOfferReturn.attachment,
            previewAttributes = credentialFormatCreateOfferReturn.previewAttributes,
        )
        LogUtil.info(this) { "proposal accepted" }
    }

    override suspend fun createOffer(
        credentialFormats: Map<String, JsonElement>?,
        credentialExchangeRecord: CredentialExchangeRecord,
        attachmentId: String?,
    ): CredentialFormatCreateOfferReturn {
        LogUtil.info(this) { "creating offer" }
        //PrintLongLine.print("credentialFormats------- $credentialFormats")
        val anoncredsFormat = FormatGeneric.getAnonCredsFormatGeneric<AnoncredsCredentialFormat>(credentialFormats)

        val createAnoncredsOffer = CreateAnoncredsOffer(
            credentialExchangeRecord = credentialExchangeRecord,
            attachmentId = attachmentId,
            attributes = anoncredsFormat.attributes,
            credentialDefinitionId = anoncredsFormat.credentialDefinitionId,
            revocationRegistryDefinitionId = anoncredsFormat.revocationRegistryDefinitionId,
            revocationRegistryIndex = anoncredsFormat.revocationRegistryIndex,
            linkedAttachments = anoncredsFormat.linkedAttachments,
        )
        val credentialFormatCreateOfferReturn = createAnonCredsOffer(createAnoncredsOffer)

        LogUtil.info(this) { "offer created" }
        return CredentialFormatCreateOfferReturn(
            format = credentialFormatCreateOfferReturn.format,
            attachment = credentialFormatCreateOfferReturn.attachment,
            previewAttributes = credentialFormatCreateOfferReturn.previewAttributes,
        )
    }

    override suspend fun processOffer(
        attachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord,
    ) {
        LogUtil.info(this) { "processing offer - ${credentialExchangeRecord.logSummary()}" }

        val offer = AnonCredsCredentialOffer.fromAttachment(attachment) // FormatDataUtil.parseAttachmentData<AnonCredsCredentialOffer>(attachment)
        if (offer.schemaId.isBlank() || offer.credDefId.isBlank()) {
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
        LogUtil.info(this) { "accepting offer" }
        val offer = AnonCredsCredentialOffer.fromAttachment(attachment) // FormatDataUtil.parseAttachmentData<AnonCredsCredentialOffer>(attachment)

        val credentialOfferJson = offerCredentialMessageV2.getCredentialOfferAttach(attachment.id)
        val credentialOffer = CredentialOffer(credentialOfferJson)
        // PrintLongLine.print(">>>> offer: ${offer.toString()}")

        val cd = agent.ledgerService.getCredentialDefinition(offer.credDefId)
        val credentialDefinition = cd.replace("\\\"", "\"")
        val linkSecret = agent.anoncredsService.getLinkSecret(agent.wallet.linkSecretId!!)
        val holderDid = getHolderDid(credentialExchangeRecord)

        var credentialDefinitionUniffi: CredentialDefinition? = null
        try {
            credentialDefinitionUniffi =
                CredentialDefinition(credentialDefinition)
            //PrintLongLine.print(">>>> cred def uniffi: ${credentialDefinitionUniffi.toJson()}")
        } catch (e: Throwable) {
            LogUtil.error(this, e) { "error anoncred uniffi: ${e.message}" }
        }

        var ct: CredentialRequestTuple? = null

        // to do get
        // val isLegacyIdentifier = Indyidentifiers.isUnqualifiedCredentialDefinitionId(credentialOffer.credDefId)
        // val entropy = if ((useLegacyProverDid!=null && !useLegacyProverDid) || !isLegacyIdentifier) Verifier().generateNonce() else null //[TODO] anoncreds came from uniffi
        val entropy = Verifier().generateNonce() // [TODO] anoncreds came from uniffi
        try {
            ct = Prover().createCredentialRequest(
                entropy,
                null,
                credentialDefinitionUniffi!!,
                linkSecret,
                agent.wallet.linkSecretId!!,
                credentialOffer,
            )
        } catch (e: Throwable) {
            LogUtil.error(this, e) { "Prover().createCredentialRequest error ${e.message}" }
        }

        val credReqTuple = ct!!
        val credentialRequestMetadata = credReqTuple.metadata

        credentialExchangeRecord.metadata.set(
            MetadataKeys.AnonCredsCredentialRequestMetadataKey,
            Json.encodeToJsonElement(credReqTuple.metadata.toJson()),
        )

        credentialExchangeRecord.metadata.set(
            MetadataKeys.AnonCredsCredentialMetadataKey,
            Json.encodeToJsonElement(
                AnonCredsCredentialMetadata(
                    schemaId = offer.schemaId,
                    credentialDefinitionId = offer.credDefId,
                ),
            ),
        )

        val format = Format(
            attachId = attachmentId ?: BaseRecord.generateId(),
            format = ANONCREDS_CREDENTIAL_REQUEST,
        )

        val attachment = Attachment.fromData(
            credReqTuple.request.toJson().toByteArray(),
            format.attachId,
        )

        return CredentialFormatCreateReturn(
            attachment = attachment,
            format = format,
        )
    }

    override suspend fun createRequest(
        credentialFormats: List<Format>?,
        credentialExchangeRecord: CredentialExchangeRecord,
    ): CredentialFormatCreateReturn {
        throw CredoError("Starting from a request is not supported for anoncreds credentials")
    }

    /**
     * We don't have any models to validate an anoncreds request object, for now this method does nothing
     */
    override suspend fun processRequest(
        attachment: Attachment,
        credentialExchangeRecord: CredentialExchangeRecord,
    ) {
        // not needed for anoncreds
    }

    override suspend fun acceptRequest(
        requestAttachment: Attachment,
        offerAttachment: Attachment?,
        credentialExchangeRecord: CredentialExchangeRecord,
        credentialFormats: Map<String, JsonElement>?,
        requestAppendAttachments: List<Attachment>?,
        attachmentId: String?,
    ): CredentialFormatCreateReturn {
        LogUtil.info(this) { "accepting request" }
        val credentialAttributes = credentialExchangeRecord.credentialAttributes
        if (credentialAttributes == null) {
            throw CredoError("Missing required credential attribute values on credential record with id ${credentialExchangeRecord.id}")
        }

        if (offerAttachment == null) throw CredoError("Missing offer attachments in accept request")
        val credentialOffer = FormatDataUtil.parseAttachmentData<AnonCredsCredentialOffer>(offerAttachment) ?: throw CredoError("Missing anoncreds credential offer in createCredential")
        val credentialRequest = FormatDataUtil.parseAttachmentData<AnonCredsCredentialRequest>(requestAttachment) ?: throw CredoError("Missing anoncreds credential request in createCredential")

        val anonCredsCredentialDefinitionRecord = agent.anoncredsCredentialDefinitionRepository.getByCredentialDefinitionId(credentialRequest.credDefId)
        val credentialDefinition = anonCredsCredentialDefinitionRecord.credentialDefinition
        val revocation = credentialDefinition.value.revocation

        var revocationRegistryDefinitionId: String? = null
        var revocationRegistryIndex: Int? = null
        var revocationStatusList: AnonCredsRevocationStatusList? = null

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
                    "Revocation registry $revocationRegistryDefinitionId is in ${revocationRegistryDefinitionPrivateRecord.state} state",
                )
            }

            revocationStatusList = AnonCredsObjects.fetchRevocationStatusList(
                agent,
                revocationRegistryDefinitionId,
                dateToTimestamp(Date()).toULong(),
            )
        }

        val createCredentialOptions = CreateCredentialOptions(
            credentialOffer = credentialOffer, // AQUI NAO PODE SE RNULO
            credentialRequest = credentialRequest,
            credentialValues = Credential.convertAttributesToCredentialValues(credentialAttributes),
            revocationRegistryDefinitionId = revocationRegistryDefinitionId,
            revocationStatusList = revocationStatusList,
            revocationRegistryIndex = revocationRegistryIndex,
        )
        val createCredentialReturn = agent.anonCredsIssuerService.createCredential(createCredentialOptions)
        val credentialRevocationId = createCredentialReturn.credentialRevocationId
        val credential = createCredentialReturn.credential

        if (credential.revRegId != null) {
            credentialExchangeRecord.addMetadata(
                MetadataKeys.AnonCredsCredentialMetadataKey,
                Json.encodeToJsonElement(
                    AnonCredsCredentialMetadata(
                        revocationRegistryId = revocationRegistryDefinitionId,
                        credentialRevocationId = createCredentialReturn.credentialRevocationId,
                    ),
                ),
            )

            if (revocationRegistryDefinitionId != null && credentialRevocationId != null) {
                val tags: Tags = mapOf(
                    "anonCredsRevocationRegistryId" to revocationRegistryDefinitionId,
                    "anonCredsCredentialRevocationId" to credentialRevocationId,
                )
                credentialExchangeRecord.setTags(tags)
            }
        }

        val format = Format(
            attachId = attachmentId ?: BaseRecord.generateId(),
            format = ANONCREDS_CREDENTIAL,
        )

        val attachment = FormatDataUtil.getFormatData(credential, format.attachId)


        LogUtil.info(this) { "request accepted" }
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
        LogUtil.info(this) { "processing credential" }
        val credentialRequestMetadata: JsonElement? = credentialExchangeRecord.metadata.get(MetadataKeys.AnonCredsCredentialRequestMetadataKey)

        if (credentialRequestMetadata == null) {
            throw CredoError("Missing required request metadata for credential exchange with thread id with id ${credentialExchangeRecord.id}")
        }

        if (credentialExchangeRecord.credentialAttributes == null ||
            (credentialExchangeRecord.credentialAttributes != null && credentialExchangeRecord.credentialAttributes!!.isEmpty())
        ) {
            throw CredoError("Missing credential attributes on credential record. Unable to check credential attributes")
        }

        val decodedString = Base64Operations.fromBase64ToStr(attachment.data.base64)
        val anonCredsCredential: AnonCredsCredential = Json.decodeFromString(decodedString)

        val credentialDefinitionResult =
            agent.ledgerService.getCredentialDefinition(anonCredsCredential.credDefId)

        val anoncredscredentialDefinition = Json.decodeFromString<AnonCredsCredentialDefinition>(credentialDefinitionResult)

        val fetchSchemaReturn_aux = agent.ledgerService.getSchema(anonCredsCredential.schemaId)
        val jsonElementSchema: JsonElement = Json.parseToJsonElement(fetchSchemaReturn_aux.first)
        val fetchSchemaReturn: FetchSchemaReturn = FetchSchemaReturn.fromJson(jsonElementSchema, anonCredsCredential.schemaId)

        credentialExchangeRecord.credentialDefinitionId = anonCredsCredential.credDefId

        var revocationRegistryResult: FetchIntermediateRevocationRegistryDefinitionResult? = null
        if (anonCredsCredential.revRegId != null) {
            val revocation = agent.ledgerService.getRevocationRegistryDefinition(anonCredsCredential.revRegId)
            revocationRegistryResult = Json.decodeFromString<FetchIntermediateRevocationRegistryDefinitionResult>(revocation)
            revocationRegistryResult.revocationRegistryDefinitionId = anonCredsCredential.revRegId
            credentialExchangeRecord.updateRevocationInfos(anonCredsCredential.revRegId, revocationRegistryResult.revocationRegistryDefinitionId)
        }

        val revocationRegistryJson =
            anonCredsCredential.revRegId?.let { agent.ledgerService.getRevocationRegistryDefinition(it) }

        val revocationRegistry = revocationRegistryJson?.let { RevocationRegistryDefinition(it) }
        if (revocationRegistry != null) {
            GlobalScope.launch {
                agent.revocationService.downloadTails(revocationRegistry)
            }
        }

        val recordCredentialValues = if (credentialExchangeRecord.credentialAttributes != null) {
            Credential.convertAttributesToCredentialValues(credentialExchangeRecord.credentialAttributes!!)
        } else {
            throw CredoError("Missing credential attributes on credential record. Unable to check credential attributes")
        }

        var revocationRegistryInfo: RevocationRegistryInfo? = null
        if (revocationRegistryResult != null && revocationRegistryResult.revocationRegistryDefinitionId != null) {
            val revRegDefinition = AnonCredsRevocationRegistryDefinition(
                issuerId = revocationRegistryResult.issuerId,
                revocDefType = revocationRegistryResult.revocDefType,
                credDefId = revocationRegistryResult.credDefId,
                tag = revocationRegistryResult.tag,
                value = revocationRegistryResult.value,
            )
            revocationRegistryInfo = RevocationRegistryInfo(
                id = revocationRegistryResult.revocationRegistryDefinitionId!!,
                definition = revRegDefinition,
            )
        }

        Credential.assertCredentialValuesMatch(anonCredsCredential.values, recordCredentialValues)

        val escaped = credentialRequestMetadata.toString()
        val jsonLiteral = Json.parseToJsonElement(escaped).jsonPrimitive
        val unescaped = jsonLiteral.content
        val metadataObject = Json.parseToJsonElement(unescaped).jsonObject
        val linkSecretBlindingDataStr = metadataObject.get("link_secret_blinding_data").toString()
        val linkSecretBlindingData = Json.decodeFromString<AnonCredsLinkSecretBlindingData>(linkSecretBlindingDataStr)

        val anonCredsCredentialRequestMetadata = AnonCredsCredentialRequestMetadata(
            link_secret_blinding_data = linkSecretBlindingData,
            link_secret_name = metadataObject.get("link_secret_name").toString(),
            nonce = metadataObject.get("nonce").toString(),
        )

        val storeCredential = StoreCredentialOptions(
            credential = anonCredsCredential,
            credentialRequestMetadata = anonCredsCredentialRequestMetadata,
            credentialDefinition = anoncredscredentialDefinition,
            schema = fetchSchemaReturn.schema,
            schemaId = anonCredsCredential.schemaId,
            credentialDefinitionId = anonCredsCredential.credDefId,
            credentialId = BaseRecord.generateId(),
            revocationRegistry = revocationRegistryInfo,
        )

        credentialExchangeRecord.updateSchema(anonCredsCredential.schemaId, fetchSchemaReturn.schema)

        val storeCredentialOptions = StoreCredential.getStoreCredentialOptions(
            options = storeCredential,
        )

        val credentialId: String = agent.anonCredsHolderService.storeCredential(
            options = storeCredentialOptions,
        )

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
        }

        credentialExchangeRecord.w3cCredentialId = credentialId
        credentialExchangeRecord.credentials.add(
            CredentialRecordBinding(
                credentialRecordType = this.credentialRecordType,
                credentialRecordId = credentialId,
            ),
        )

        try {
            agent.credentialExchangeRepository.update(credentialExchangeRecord)
        } catch (e: Throwable) {
            LogUtil.error(this, e) { "error updating credential = ${credentialExchangeRecord.logSummary()}" }
        }
    }

    override suspend fun shouldAutoRespondToProposal(
        credentialRecord: CredentialExchangeRecord,
        offerAttachment: Attachment,
        proposalAttachment: Attachment,
    ): Boolean {
        val credentialOffer = FormatDataUtil.parseAttachmentData<AnonCredsCredentialOffer>(offerAttachment) ?: throw CredoError("Missing anoncreds credential offer in shouldAutoRespondToProposal")
        val proposal = FormatDataUtil.parseAttachmentData<AnonCredsCredentialProposalFormat>(proposalAttachment) ?: throw CredoError("Missing anoncreds credential proposal in shouldAutoRespondToProposal")
        return proposal.credDefId == credentialOffer.credDefId
    }

    override suspend fun shouldAutoRespondToOffer(
        credentialRecord: CredentialExchangeRecord,
        offerAttachment: Attachment,
        proposalAttachment: Attachment,
    ): Boolean {
        val credentialOffer = FormatDataUtil.parseAttachmentData<AnonCredsCredentialOffer>(offerAttachment) ?: throw CredoError("Missing anoncreds credential offer in shouldAutoRespondToProposal")
        val proposal = FormatDataUtil.parseAttachmentData<AnonCredsCredentialProposalFormat>(proposalAttachment) ?: throw CredoError("Missing anoncreds credential proposal in shouldAutoRespondToProposal")
        return proposal.credDefId == credentialOffer.credDefId
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
            ANONCREDS_CREDENTIAL_REQUEST,
            ANONCREDS_CREDENTIAL_OFFER,
            ANONCREDS_CREDENTIAL_FILTER,
            ANONCREDS_CREDENTIAL,
        )

        return formatIdentifier in supportedFormats
    }

    private suspend fun createAnonCredsOffer(createAnoncredsOffer: CreateAnoncredsOffer): CredentialFormatCreateOfferReturn {
        val credentialExchangeRecord = createAnoncredsOffer.credentialExchangeRecord
        val revocationRegistryDefinitionId = createAnoncredsOffer.revocationRegistryDefinitionId
        val credentialDefinitionId = createAnoncredsOffer.credentialDefinitionId
        val revocationRegistryIndex = createAnoncredsOffer.revocationRegistryIndex

        val format = Format(
            attachId = createAnoncredsOffer.attachmentId ?: BaseRecord.generateId(),
            format = ANONCREDS_CREDENTIAL_OFFER,
        )

        val anoncredsCredentialOffer = agent.anonCredsIssuerService.createCredentialOffer(credentialDefinitionId)

        val (attachments, previewAttributes) = FormatDataUtil.getCredentialLinkedAttachments(
            attributes = createAnoncredsOffer.attributes,
            linkedAttachments = createAnoncredsOffer.linkedAttachments,
        )

        if (previewAttributes == null) {
            throw CredoError("Missing required preview attributes for anoncreds offer")
        }

        FormatDataUtil.assertPreviewAttributesMatchSchemaAttributes(agent, anoncredsCredentialOffer, previewAttributes)

        val anonCredsCredentialDefinitionRecord = agent.anoncredsCredentialDefinitionRepository.getByCredentialDefinitionId(credentialDefinitionId)
        val credentialDefinition = anonCredsCredentialDefinitionRecord.credentialDefinition.value

        if (credentialDefinition.revocation != null) {
            if (revocationRegistryDefinitionId == null || revocationRegistryIndex == null) {
                throw CredoError("AnonCreds revocable credentials require revocationRegistryDefinitionId and revocationRegistryIndex")
            }

            val tags: Tags = mapOf(
                "anonCredsRevocationRegistryId" to revocationRegistryDefinitionId,
                "anonCredsCredentialRevocationId" to revocationRegistryIndex.toString(),
            )
            credentialExchangeRecord.setTags(tags)
        }

        credentialExchangeRecord.metadata.set(
            MetadataKeys.AnonCredsCredentialMetadataKey,
            Json.encodeToJsonElement(
                AnonCredsCredentialMetadata(
                    schemaId = anoncredsCredentialOffer.schemaId,
                    credentialDefinitionId = anoncredsCredentialOffer.credDefId,
                    credentialRevocationId = revocationRegistryIndex.toString(),
                    revocationRegistryId = revocationRegistryDefinitionId,
                ),
            ),
        )

        val attachment = FormatDataUtil.getFormatData(anoncredsCredentialOffer, format.attachId)

        return CredentialFormatCreateOfferReturn(
            format = format,
            attachment = attachment,
            previewAttributes = previewAttributes,
        )
    }

    fun dateToTimestamp(date: Date): Long = date.time / 1000

    private suspend fun getHolderDid(credentialRecord: CredentialExchangeRecord): String {
        if (credentialRecord.connectionId == null) {
            throw CredoError("Connection id not found")
        }
        val connection = agent.connectionRepository.getById(credentialRecord.connectionId!!)
        return connection.did
    }
}
