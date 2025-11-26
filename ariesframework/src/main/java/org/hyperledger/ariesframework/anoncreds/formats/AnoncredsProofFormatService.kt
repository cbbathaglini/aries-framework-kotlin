package org.hyperledger.ariesframework.anoncreds.formats

import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.RevocationRegistryDefinition
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.AttachmentData
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsCredentialsForProofRequest
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsGetCredentialsForProofRequestOptions
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsPresentationPreviewAttribute
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsPresentationPreviewPredicate
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsProposeProofFormat
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsRequestedAttributeMatch
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsRequestedPredicateMatch
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsSelectedCredentials
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnoncredsProofFormat
import org.hyperledger.ariesframework.anoncreds.formats.utils.DuplicateNames
import org.hyperledger.ariesframework.anoncreds.formats.utils.FormatGeneric
import org.hyperledger.ariesframework.anoncreds.formats.utils.GetCredentialsForProofRequestReferent
import org.hyperledger.ariesframework.anoncreds.formats.utils.RevocationRegistries
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinitions
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProof
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequestRestriction
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedAttribute
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedPredicate
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryEntry
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchemas
import org.hyperledger.ariesframework.anoncreds.model.RevocationRegistriesForRequestResult
import org.hyperledger.ariesframework.anoncreds.model.RevocationRegistryValue
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateProofOptions
import org.hyperledger.ariesframework.anoncreds.utils.AnonCredsEncoder
import org.hyperledger.ariesframework.credentials.utils.JsonEncoder
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.proofs.models.PredicateType
import org.hyperledger.ariesframework.proofs.models.ProofFormatCreateReturn
import org.hyperledger.ariesframework.proofs.models.ProofFormatProcessOptions
import org.hyperledger.ariesframework.proofs.models.ProofFormatSpec
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.proofs.repository.verifier.VerifierRecord
import org.hyperledger.ariesframework.proofs.utils.ProofRequestOperations
import org.hyperledger.ariesframework.proofs.utils.RequestsEquals
import org.hyperledger.ariesframework.proofs.v2.ProofUtils
import org.hyperledger.ariesframework.proofs.v2.formats.ProofFormatService
import org.hyperledger.ariesframework.proofs.v2.messages.PresentationMessageV2
import org.hyperledger.ariesframework.proofs.v2.messages.RequestPresentationMessageV2
import org.hyperledger.ariesframework.proofs.v2.verifier.VerifyProofOptions
import org.hyperledger.ariesframework.util.concurrentForEach
import org.slf4j.LoggerFactory

class AnoncredsProofFormatService(
    override val formatKey: String = "anoncreds",
    val agent: Agent,
) : ProofFormatService<AnoncredsProofFormat> {

    private val logger = LoggerFactory.getLogger(AnoncredsCredentialFormatService::class.java)

    companion object {
        const val ANONCREDS_PRESENTATION_PROPOSAL = "anoncreds/proof-request@v1.0"
        const val ANONCREDS_PRESENTATION_REQUEST = "anoncreds/proof-request@v1.0"
        const val ANONCREDS_PRESENTATION = "anoncreds/proof@v1.0"
    }

    override suspend fun createProposal(
        profRecord: ProofExchangeRecord,
        attachmentId: String?,
        proofFormats: Map<String, JsonElement>,
    ): ProofFormatCreateReturn {
        val format = ProofFormatSpec(
            attachmentId = attachmentId!!,
            format = ANONCREDS_PRESENTATION_PROPOSAL,
        )

        val anoncredsFormat =
            FormatGeneric.getAnonCredsFormatGeneric<AnonCredsProposeProofFormat>(proofFormats)

        val proofRequest = createRequestFromPreview(
            name = anoncredsFormat.name ?: "Proof request",
            version = anoncredsFormat.version ?: "1.0",
            nonce = agent.anonCredsHolderService.generateNonce(), // revisar implementacao
            attributes = anoncredsFormat.attributes ?: emptyList(),
            predicates = anoncredsFormat.predicates ?: emptyList(),
            nonRevokedInterval = anoncredsFormat.nonRevokedInterval,
        )
        logger.info("proofRequest: ${proofRequest.toJson()}")

        val attachment = this.getFormatData(proofRequest, format.attachmentId!!)

        return ProofFormatCreateReturn(
            format = format,
            attachment = attachment,
        )
    }

    override suspend fun processProposal(attachment: Attachment, proofRecord: ProofExchangeRecord) {
        val json = attachment.getDataAsJson()
        val proposalJson: AnonCredsProofRequest =
            Json.decodeFromString<AnonCredsProofRequest>(attachment.getDataAsJson())
        DuplicateNames.assertNoDuplicateGroupsNamesInProofRequest(proposalJson)
    }

    override suspend fun acceptProposal(
        proofRecord: ProofExchangeRecord,
        attachmentId: String?,
        proposalAttachment: Attachment,
        proofFormats: Map<String, JsonElement>?,
    ): ProofFormatCreateReturn {
        val format = ProofFormatSpec(
            attachmentId = attachmentId!!,
            format = ANONCREDS_PRESENTATION_REQUEST,
        )
        val proposalJson: AnonCredsProofRequest =
            Json.decodeFromString<AnonCredsProofRequest>(proposalAttachment.getDataAsJson())

        val request = proposalJson.copy(
            nonce = agent.anonCredsHolderService.generateNonce(),
        )

        val attachment = this.getFormatData(request, format.attachmentId!!)

        return ProofFormatCreateReturn(
            format = format,
            attachment = attachment,
        )
    }

    override suspend fun createRequest(
        proofRecord: ProofExchangeRecord,
        attachmentId: String?,
        proofFormats: Map<String, JsonElement>?,
    ): ProofFormatCreateReturn {
        logger.info("createRequest in anoncredsproof format service")

        val format = ProofFormatSpec(
            format = ANONCREDS_PRESENTATION_REQUEST,
            attachmentId = attachmentId!!,
        )

        logger.info("format: ${format.format}")
        val anoncredsFormat =
            FormatGeneric.getAnonCredsFormatGeneric<AnonCredsProposeProofFormat>(proofFormats).normalizeFields()

        logger.info("anoncredsFormat:>>>> $anoncredsFormat")
        logger.info("anoncredsFormat.attributes:>>>> ${anoncredsFormat.attributes}")

        val request: AnonCredsProofRequest = createRequestFromPreview(
            name = anoncredsFormat.name ?: "Proof request", // else eu coloquei
            version = anoncredsFormat.version ?: "1.0", // else eu coloquei
            nonce = agent.anonCredsHolderService.generateNonce(), // revisar implementacao
            attributes = anoncredsFormat.attributes ?: emptyList(),
            predicates = anoncredsFormat.predicates ?: emptyList(),
            nonRevokedInterval = anoncredsFormat.nonRevokedInterval,
        )
        logger.info("AnonCredsProofRequest: ${request.toJson()}")

        // Assert attribute and predicate (group) names do not match
        DuplicateNames.assertNoDuplicateGroupsNamesInProofRequest(request)
        logger.info("after duplicate")

        val anoncredsRequest: JsonElement = Json.encodeToJsonElement(AnonCredsProofRequest.serializer(), request)
        logger.info("anoncreds request: $anoncredsRequest")

        logger.info("request: ${format.attachmentId}")
        val attachment = this.getFormatData(anoncredsRequest, format.attachmentId!!)

        return ProofFormatCreateReturn(
            attachment = attachment,
            format = format,
        )
    }

    override suspend fun processRequest(options: ProofFormatProcessOptions) {
        val attachment = options.attachment
        val anonCredsProofRequest: AnonCredsProofRequest =
            Json.decodeFromString<AnonCredsProofRequest>(attachment.getDataAsJson())

        val verifierRecord = VerifierRecord(
            proofRequest = anonCredsProofRequest,
            globalThreadId = options.proofRecord.threadId,
        )

        agent.verifierRepository.save(verifierRecord)
        logger.info("💾 Salvo verifier id=${verifierRecord.id} threadid=${verifierRecord.globalThreadId ?: "null"}")

        DuplicateNames.assertNoDuplicateGroupsNamesInProofRequest(anonCredsProofRequest)
    }

    override suspend fun acceptRequest(
        requestMessage: RequestPresentationMessageV2,
        proofRecord: ProofExchangeRecord,
        proofFormats: Map<String, JsonElement>?,
        attachmentId: String,
        requestAttachment: Attachment,
        proposalAttachment: Attachment?,
        chosenCredentialId: String?
    ): ProofFormatCreateReturn {

        logger.info("requestAttachment: ${requestAttachment.getDataAsJson()}")
        val requestJson: AnonCredsProofRequest =
            Json.decodeFromString<AnonCredsProofRequest>(requestAttachment.getDataAsJson())
        logger.info("AnonCredsProofRequest: $requestJson")
        logger.info("AnonCredsProofRequest: ${requestJson.toJson()}")

        val anoncredsSelected: AnonCredsSelectedCredentials = _selectCredentialsForRequest(
            proofRequest = requestJson,
            chosenCredentialId = chosenCredentialId,
            options = AnonCredsGetCredentialsForProofRequestOptions(
                filterByNonRevocationRequirements = true,
            ),
        )

        val anoncredsFormat = AnonCredsSelectedCredentials.convert(proofFormats)
        logger.info("anoncredsFormat: $anoncredsFormat")
        anoncredsFormat.attributes.values.forEach { item ->
            logger.info("item::::: ${item.credentialId}")
            logger.info("item::::: ${item.revealed}")
            logger.info("item::::: ${item.credentialInfo.toJsonElement()}")
        }

        val selectedCredentials: AnonCredsSelectedCredentials = anoncredsFormat ?: anoncredsSelected
        logger.info("selectedCredentials: $selectedCredentials")
        val format = ProofFormatSpec(
            format = ANONCREDS_PRESENTATION,
            attachmentId = attachmentId,
        )

        val proof = createProof(requestMessage, requestJson, anoncredsSelected, proofFormats)

        val anonCredsProofjsonElement = Json.encodeToJsonElement(
            serializer<AnonCredsProof>(),
            proof,
        )
        logger.info("anonCredsProofjsonElement: $anonCredsProofjsonElement")
//        val json = Json.encodeToString(anonCredsProofjsonElement)
//        val base64 = Base64.getEncoder().encodeToString(json.toByteArray())
        val attachment = Attachment(
            id = attachmentId,
            mimetype = "application/json",
            data = AttachmentData(
                base64 = JsonEncoder.toBase64(anonCredsProofjsonElement),
            ),
        )

        return ProofFormatCreateReturn(
            attachment = attachment,
            format = format,
        )
    }

    override suspend fun processPresentation(
        requestAttachment: Attachment,
        presentationAttachment: Attachment,
        proofRecord: ProofExchangeRecord,
        presentationMessage: PresentationMessageV2,
        requestMessage: RequestPresentationMessageV2,
    ): Boolean {
        val requestJson: AnonCredsProofRequest =
            Json.decodeFromString<AnonCredsProofRequest>(requestAttachment.getDataAsJson())
        logger.info("request json: $requestJson")

        val anonCredsProof: AnonCredsProof =
            Json.decodeFromString<AnonCredsProof>(presentationAttachment.getDataAsJson())
        logger.info("anonCredsProof: $anonCredsProof")

        for ((referent, attribute) in anonCredsProof.requestedProof.revealedAttrs) {
            if (!checkValidCredentialValueEncoding(attribute.raw, attribute.encoded)) {
                throw CredoError(
                    "The encoded value for '$referent' is invalid. " +
                        "Expected '${AnonCredsEncoder.encodeCredentialValue(attribute.raw)}'. " +
                        "Actual '${attribute.encoded}'",
                )
            }
        }

        for ((_, attributeGroup) in anonCredsProof.requestedProof.revealedAttrGroups.orEmpty()) {
            for ((attributeName, attribute) in attributeGroup.values) {
                if (!checkValidCredentialValueEncoding(attribute.raw, attribute.encoded)) {
                    throw CredoError(
                        "The encoded value for '$attributeName' is invalid. " +
                            "Expected '${AnonCredsEncoder.encodeCredentialValue(attribute.raw)}'. " +
                            "Actual '${attribute.encoded}'",
                    )
                }
            }
        }

        val schemasMap: Map<String, AnonCredsSchema> =
            agent.ledgerService.getSchemas(anonCredsProof.identifiers.map { it.schemaId }.toSet())
        val schemas = AnonCredsSchemas(schemasMap)
        logger.info("schemas: $schemas")

        val credentialDefinitionsMap: Map<String, AnonCredsCredentialDefinition> =
            ProofUtils.getCredentialDefinitions(
                agent,
                anonCredsProof.identifiers.map { it.credDefId }.toSet(),
            )

        logger.info("credentialDefinitionsMap: $credentialDefinitionsMap")
        val credentialDefinitionUniffiMap: Map<String, CredentialDefinition> =
            convert(credentialDefinitionsMap)
        logger.info("credentialDefinitionUniffiMap: $credentialDefinitionUniffiMap")

        val revocationRegistries =
            RevocationRegistries(agent).getRevocationRegistriesForProof(anonCredsProof)
        logger.info("revocationRegistries: $revocationRegistries")

        val anonCredsCredentialDefinitions = AnonCredsCredentialDefinitions(
            credentialDefinitions = credentialDefinitionsMap,
        )
        logger.info("anonCredsCredentialDefinitions: $anonCredsCredentialDefinitions")

        proofRecord.isVerified = agent.anoncredsVerifierService.verifyProof(
            options = VerifyProofOptions(
                proofRequest = requestJson,
                proof = anonCredsProof,
                presentationMessage = presentationMessage,
                schemas = schemas,
                credentialDefinitions = anonCredsCredentialDefinitions,
                revocationRegistries = revocationRegistries,
                requestMessage = requestMessage,
            ),
        )
        logger.info("is verified: ${proofRecord.isVerified}")
        return proofRecord.isVerified!!
    }

    suspend fun convert(
        input: Map<String, AnonCredsCredentialDefinition>,
    ): Map<String, CredentialDefinition> {
        return input.mapValues { (credDefId, def) ->
            val credDef = agent.ledgerService.getCredentialDefinition(credDefId)
            CredentialDefinition(credDef)
        }
    }

//    suspend fun verifyProof(proofRequest: String, proof: String): Boolean = coroutineScope {
//        logger.debug("verifying proof: $proof")
//        val partialProof = Json { ignoreUnknownKeys = true }.decodeFromString<PartialProof>(proof)
//        val schemas = async { getSchemas(partialProof.identifiers.map { it.schemaId }.toSet()) }
//        val credentialDefinitions = async {
//            getCredentialDefinitions(partialProof.identifiers.map { it.credentialDefinitionId }
//                .toSet())
//        }
//        val revocationRegistryDefinitions =
//            async {
//                getRevocationRegistryDefinitions(partialProof.identifiers.mapNotNull { it.revocationRegistryId }
//                    .toSet())
//            }
//        val revocationStatusLists = agent.revocationService.getRevocationStatusLists(
//            partialProof,
//            revocationRegistryDefinitions.await()
//        )
//
//        val credentialDefinitionUniffiMap : Map<String, CredentialDefinition> = convert(credentialDefinitions)
//
//        return@coroutineScope try {
//            Verifier().verifyPresentation(
//                Presentation(proof),
//                PresentationRequest(proofRequest),
//                schemas.await(),
//                credentialDefinitions.await(),
//                revocationRegistryDefinitions.await(),
//                revocationStatusLists,
//                null,
//            )
//        } catch (e: Exception) {
//            logger.error("Error verifying proof: $e")
//            false
//        }
//    }

    suspend fun getRevocationRegistryDefinitions(revocationRegistryIds: Set<String>): Map<String, RevocationRegistryDefinition> {
        val revocationRegistryDefinitions = mutableMapOf<String, RevocationRegistryDefinition>()
        val lock = Mutex()

        revocationRegistryIds.concurrentForEach { revocationRegistryId ->
            val revocationRegistryDefinition =
                agent.ledgerService.getRevocationRegistryDefinition(revocationRegistryId)
            lock.withLock {
                revocationRegistryDefinitions[revocationRegistryId] =
                    RevocationRegistryDefinition(revocationRegistryDefinition)
            }
        }

        return revocationRegistryDefinitions
    }

    override suspend fun getCredentialsForRequest(
        proofRecord: ProofExchangeRecord,
        proofFormats: Map<String, JsonElement>?,
        requestAttachment: Attachment,
        proposalAttachment: Attachment?,
    ): AnonCredsCredentialsForProofRequest {
        val proofRequestJson: AnonCredsProofRequest =
            Json.decodeFromString<AnonCredsProofRequest>(requestAttachment.getDataAsJson())

        // TODO se o valor de filterByNonRevocationRequirements for false vem ele, senao por padrao é true, achar dentro
        // um filterByNonRevocationRequirements
        val anoncredsFormat =
            FormatGeneric.getAnonCredsFormatGeneric<AnonCredsSelectedCredentials>(proofFormats)

        val anonCredsCredentialsForProofRequest: AnonCredsCredentialsForProofRequest =
            GetCredentialsForProofRequestReferent.getCredentialsForAnonCredsProofRequest(
                agent = agent,
                proofRequest = proofRequestJson,
                options = AnonCredsGetCredentialsForProofRequestOptions(
                    filterByNonRevocationRequirements = true,
                ),
            )

        return anonCredsCredentialsForProofRequest
    }

    override suspend fun selectCredentialsForRequest(
        proofRecord: ProofExchangeRecord,
        proofFormats: Map<String, JsonElement>?,
        requestAttachment: Attachment,
        proposalAttachment: Attachment?,
    ): AnonCredsSelectedCredentials {
        val proofRequestJson: AnonCredsProofRequest =
            Json.decodeFromString<AnonCredsProofRequest>(requestAttachment.getDataAsJson())

        // TODO se o valor de filterByNonRevocationRequirements for false vem ele, senao por padrao é true, achar dentro
        // um filterByNonRevocationRequirements
        val anoncredsFormat =
            FormatGeneric.getAnonCredsFormatGeneric<AnonCredsSelectedCredentials>(proofFormats)

        val selectedCredentials: AnonCredsSelectedCredentials = _selectCredentialsForRequest(
            proofRequest = proofRequestJson,
            options = AnonCredsGetCredentialsForProofRequestOptions(
                filterByNonRevocationRequirements = true,
            ),
        )

        return selectedCredentials
    }

    override suspend fun shouldAutoRespondToProposal(
        proofRecord: ProofExchangeRecord,
        proposalAttachment: Attachment,
        requestAttachment: Attachment,
    ): Boolean {
        val proposalJson: AnonCredsProofRequest =
            Json.decodeFromString<AnonCredsProofRequest>(proposalAttachment.getDataAsJson())
        val requestJson: AnonCredsProofRequest =
            Json.decodeFromString<AnonCredsProofRequest>(requestAttachment.getDataAsJson())

        val areRequestsEquals =
            RequestsEquals.areAnonCredsProofRequestsEqual(proposalJson, requestJson)

        logger.debug("AnonCreds request and proposal are are equal: $areRequestsEquals > proposal: $proposalJson || request: $requestJson")
        return areRequestsEquals
    }

    override suspend fun shouldAutoRespondToRequest(
        proofRecord: ProofExchangeRecord,
        requestAttachment: Attachment,
        proposalAttachment: Attachment,
    ): Boolean {
        val proposalJson: AnonCredsProofRequest =
            Json.decodeFromString<AnonCredsProofRequest>(proposalAttachment.getDataAsJson())
        val requestJson: AnonCredsProofRequest =
            Json.decodeFromString<AnonCredsProofRequest>(requestAttachment.getDataAsJson())

        return RequestsEquals.areAnonCredsProofRequestsEqual(proposalJson, requestJson)
    }

    override suspend fun shouldAutoRespondToPresentation(
        proofRecord: ProofExchangeRecord,
        proposalAttachment: Attachment?,
        requestAttachment: Attachment,
        presentationAttachment: Attachment,
    ): Boolean {
        return true
    }

    override fun supportsFormat(formatIdentifier: String): Boolean {
        val supportedFormats = listOf(
            ANONCREDS_PRESENTATION_PROPOSAL,
            ANONCREDS_PRESENTATION_REQUEST,
            ANONCREDS_PRESENTATION,
        )
        return formatIdentifier in supportedFormats
    }

    private fun createRequestFromPreview(
        name: String,
        version: String,
        nonce: String,
        attributes: List<AnonCredsPresentationPreviewAttribute> = emptyList(),
        predicates: List<AnonCredsPresentationPreviewPredicate> = emptyList(),
        nonRevokedInterval: AnonCredsNonRevokedInterval? = null,
    ): AnonCredsProofRequest {
        // Agrupa atributos por referent (gerando um se faltar)
        val attributesByReferent =
            mutableMapOf<String, MutableList<AnonCredsPresentationPreviewAttribute>>()
        for (attr in attributes) {
            val referent = attr.referent ?: attr.name
            attributesByReferent.getOrPut(referent) { mutableListOf() }.add(attr)
        }

        // Converte grupos em requestedAttributes
        val requestedAttributes = mutableMapOf<String, AnonCredsRequestedAttribute>()
        for ((referent, props) in attributesByReferent) {
            val attributeName: String? = if (props.size == 1) props[0].name else null
            val attributeNames: List<String>? = if (props.size > 1) props.map { it.name } else null

            requestedAttributes[referent] = AnonCredsRequestedAttribute(
                name = attributeName,
                names = attributeNames,
                restrictions = listOf(
                    AnonCredsProofRequestRestriction(
                        credDefId = props.first().credentialDefinitionId,
                    ),
                ),
                nonRevoked = null,
            )
        }

        // Converte predicados pro requestedPredicates
        val requestedPredicates = mutableMapOf<String, AnonCredsRequestedPredicate>()
        for (pred in predicates) {
            requestedPredicates[pred.name] = AnonCredsRequestedPredicate(
                name = pred.name,
                pType = PredicateType.fromString(pred.predicateType),
                pValue = pred.threshold, // ajuste para .toLong() se seu tipo for Long
                restrictions = listOf(
                    AnonCredsProofRequestRestriction(
                        credDefId = pred.credentialDefinitionId,
                    ),
                ),
                nonRevoked = null,
            )
        }

        // Monta o objeto final
        return AnonCredsProofRequest(
            name = name,
            version = version,
            nonce = nonce,
            requestedAttributes = requestedAttributes,
            requestedPredicates = requestedPredicates,
            nonRevoked = nonRevokedInterval,
        )
    }

    private fun getFormatData(data: Any, id: String): Attachment {
        return Attachment(
            id = id,
            mimetype = "application/json",
            data = AttachmentData(
                base64 = JsonEncoder.toBase64(data),
            ),
        )
    }

    private suspend fun _selectCredentialsForRequest(
        proofRequest: AnonCredsProofRequest,
        chosenCredentialId: String? = null,
        options: AnonCredsGetCredentialsForProofRequestOptions,
    ): AnonCredsSelectedCredentials {
        val credentialsForRequest =
            GetCredentialsForProofRequestReferent.getCredentialsForAnonCredsProofRequest(
                agent = agent,
                proofRequest = proofRequest,
                chosenCredentialId = chosenCredentialId,
                options = options,
            )

        val selectedAttributes = mutableMapOf<String, AnonCredsRequestedAttributeMatch>()
        val selectedPredicates = mutableMapOf<String, AnonCredsRequestedPredicateMatch>()

        // Seleciona o primeiro match disponível para cada atributo
        for ((name, matches) in credentialsForRequest.attributes) {
            val first = matches.firstOrNull()
                ?: throw CredoError("Unable to automatically select requested attributes.")
            selectedAttributes[name] = first
        }

        // Seleciona o primeiro match disponível para cada predicado
        for ((name, matches) in credentialsForRequest.predicates) {
            val first = matches.firstOrNull()
                ?: throw CredoError("Unable to automatically select requested predicates.")
            selectedPredicates[name] = first
        }

        return AnonCredsSelectedCredentials(
            attributes = selectedAttributes,
            predicates = selectedPredicates,
            selfAttestedAttributes = emptyMap(),
        )
    }

    suspend fun createProof(
        requestMessage: RequestPresentationMessageV2,
        proofRequest: AnonCredsProofRequest,
        selectedCredentials: AnonCredsSelectedCredentials,
        proofFormats: Map<String, JsonElement>?,
    ): AnonCredsProof = coroutineScope {
        // attributes + predicates -> lista única
        val selectedEntries = buildList {
            addAll(selectedCredentials.attributes.values)
            addAll(selectedCredentials.predicates.values)
        }

        logger.info("selectedEntries: $selectedEntries")

        val credentialObjects = selectedEntries.map { c ->
            async {
                val id = when (c) {
                    is AnonCredsRequestedAttributeMatch -> c.credentialId
                    is AnonCredsRequestedPredicateMatch -> c.credentialId
                    else -> error("Tipo inesperado: ${c::class}")
                }
                agent.anonCredsHolderService.getCredential(
                    credentialId = id,
                    useUnqualifiedIdentifiersIfPresent =
                    ProofRequestOperations.proofRequestUsesUnqualifiedIdentifiers(proofRequest),
                )
            }
        }.awaitAll()

        // Carregar schemas e cred defs a partir das credenciais obtidas
        val schemaIds: Set<String> = credentialObjects.map { it.schemaId }.toSet()
        val credDefIds: Set<String> = credentialObjects.map { it.credentialDefinitionId }.toSet()

        val schemas: Map<String, AnonCredsSchema> =
            ProofUtils.getSchemas(agent, schemaIds)

        val credentialDefinitions: Map<String, AnonCredsCredentialDefinition> =
            ProofUtils.getCredentialDefinitions(agent, credDefIds)

        // Pode ajustar o tipo de retorno conforme sua função util: Pair ou data class
        val revocationRegistriesForRequestResult: RevocationRegistriesForRequestResult =
            RevocationRegistries(agent).getRevocationRegistriesForRequest(
                proofRequest,
                selectedCredentials,
            )

        val updatedSelectedCredentials =
            revocationRegistriesForRequestResult.updatedSelectedCredentials
        val revocationRegistries = revocationRegistriesForRequestResult.revocationRegistries

        val anonCredsRevocationRegistries: MutableMap<String, AnonCredsRevocationRegistryEntry> =
            mutableMapOf()

        revocationRegistries.mapValues { (key, value) ->

            val revRegValue: RevocationRegistryValue =
                Json.decodeFromString<RevocationRegistryValue>(value.definition.value.toString())

            val anonCredsRevocationRegistryDefinition = AnonCredsRevocationRegistryDefinition(
                issuerId = value.definition.issuerId,
                revocDefType = value.definition.revocDefType,
                credDefId = value.definition.credDefId,
                tag = value.definition.tag,
                value = revRegValue,
            )

            val revocationStatusListsAnoncreds: MutableMap<Long, AnonCredsRevocationStatusList> =
                value.revocationStatusLists
                    ?.mapValues { (_, v) -> AnonCredsRevocationStatusList.toAnonCreds(v) }
                    ?.toMutableMap()
                    ?: mutableMapOf()

            anonCredsRevocationRegistries.put(
                key,
                AnonCredsRevocationRegistryEntry(
                    tailsFilePath = agent.ledgerService.getTailsPath(),
                    tailsHash = value.tailsHash,
                    definition = anonCredsRevocationRegistryDefinition,
                    revocationStatusLists = revocationStatusListsAnoncreds,
                ),
            )
        }

        val anonCredsSchema = AnonCredsSchemas(
            schemas = schemas,
        )

        val anonCredsCredentialDefinitions = AnonCredsCredentialDefinitions(
            credentialDefinitions = credentialDefinitions,
        )

        // Criar a prova
        agent.anonCredsHolderService.createProof(
            options = CreateProofOptions(
                requestMessage = requestMessage,
                proofRequest = proofRequest,
                selectedCredentials = updatedSelectedCredentials,
                schemas = anonCredsSchema,
                credentialDefinitions = anonCredsCredentialDefinitions,
                revocationRegistries = anonCredsRevocationRegistries,
                proofFormats = proofFormats,
            ),
        )
    }

//    private suspend fun getCredentialDefinitions(
//        credentialDefinitionIds: Set<String>
//    ): Map<String, AnonCredsCredentialDefinition> {
//        val credentialDefinitions = mutableMapOf<String, AnonCredsCredentialDefinition>()
//        for (credDefId in credentialDefinitionIds) {
//            val credentialDefinitionResult =
//                agent.ledgerService.getCredentialDefinition(credDefId)
//            val anonCredsCredentialDefinition: AnonCredsCredentialDefinition = Json.decodeFromString(credentialDefinitionResult)
//            credentialDefinitions[credDefId] = anonCredsCredentialDefinition
//        }
//        return credentialDefinitions
//    }

    private fun checkValidCredentialValueEncoding(raw: Any, encoded: String): Boolean {
        return encoded == AnonCredsEncoder.encodeCredentialValue(raw)
    }
}
