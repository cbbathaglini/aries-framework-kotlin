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
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
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
        proofRecord: ProofExchangeRecord,
        attachmentId: String?,
        proofFormats: Map<String, JsonElement>,
    ): ProofFormatCreateReturn {

        val format = ProofFormatSpec(
            attachmentId = attachmentId!!,
            format = ANONCREDS_PRESENTATION_PROPOSAL,
        )

        val anonCredsFormat =
            FormatGeneric.getAnonCredsFormatGeneric<AnonCredsProposeProofFormat>(proofFormats)

        val proofRequest = createRequestFromPreview(
            name = anonCredsFormat.name ?: "Proof request",
            version = anonCredsFormat.version ?: "1.0",
            nonce = agent.anonCredsHolderService.generateNonce(),
            attributes = anonCredsFormat.attributes ?: emptyList(),
            predicates = anonCredsFormat.predicates ?: emptyList(),
            nonRevokedInterval = anonCredsFormat.nonRevokedInterval,
        )

        // logger.info("proofRequest: ${proofRequest.toJson()}")

        val attachment = getFormatData(proofRequest, format.attachmentId!!)

        return ProofFormatCreateReturn(
            format = format,
            attachment = attachment,
        )
    }

    override suspend fun processProposal(attachment: Attachment, proofRecord: ProofExchangeRecord) {
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

        val attachment = getFormatData(request, format.attachmentId!!)

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

        // logger.info("createRequest in anoncredsproof format service")

        val format = ProofFormatSpec(
            format = ANONCREDS_PRESENTATION_REQUEST,
            attachmentId = attachmentId!!,
        )

        // logger.info("format: ${format.format}")

        val anonCredsFormat =
            FormatGeneric.getAnonCredsFormatGeneric<AnonCredsProposeProofFormat>(proofFormats)
        // val anoncredsFormat = anonFormat.normalizeFields()

        // logger.info("anoncredsFormat:>>>> $anonCredsFormat")
        // logger.info("anoncredsFormat.attributes:>>>> ${anoncredsFormat.attributes}")

        val request: AnonCredsProofRequest = createRequestFromPreview(anonCredsFormat)

        // logger.info("AnonCredsProofRequest: ${request.toJson()}")

        DuplicateNames.assertNoDuplicateGroupsNamesInProofRequest(request)

        // logger.info("after duplicate")

        val anonCredsRequest: JsonElement =
            Json.encodeToJsonElement(AnonCredsProofRequest.serializer(), request)

        // logger.info("anoncreds request: $anonCredsRequest")
        // logger.info("request: ${format.attachmentId}")

        val attachment = getFormatData(anonCredsRequest, format.attachmentId!!)

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

        // logger.info("Saved verifier id=${verifierRecord.id} threadId=${verifierRecord.globalThreadId ?: "null"}")

        DuplicateNames.assertNoDuplicateGroupsNamesInProofRequest(anonCredsProofRequest)
    }

    override suspend fun acceptRequest(
        requestMessage: RequestPresentationMessageV2,
        proofRecord: ProofExchangeRecord,
        proofFormats: Map<String, JsonElement>?,
        attachmentId: String,
        requestAttachment: Attachment,
        proposalAttachment: Attachment?,
        chosenCredentialId: String?,
    ): ProofFormatCreateReturn {

        // logger.info("requestAttachment: ${requestAttachment.getDataAsJson()}")

        val requestJson: AnonCredsProofRequest =
            Json.decodeFromString<AnonCredsProofRequest>(requestAttachment.getDataAsJson())

        // logger.info("AnonCredsProofRequest: $requestJson")
        // logger.info("AnonCredsProofRequest: ${requestJson.toJson()}")

        val anonCredsSelected: AnonCredsSelectedCredentials =
            _selectCredentialsForRequest(
                proofRequest = requestJson,
                chosenCredentialId = chosenCredentialId,
                options = AnonCredsGetCredentialsForProofRequestOptions(
                    filterByNonRevocationRequirements = true,
                ),
            )

        validateCredentialChosen(chosenCredentialId, requestJson)

        val anonCredsFormat = AnonCredsSelectedCredentials.convert(proofFormats)

        // logger.info("anoncredsFormat: $anonCredsFormat")

        anonCredsFormat?.attributes?.values?.forEach { item ->
            // logger.info("item credentialId: ${item.credentialId}")
            // logger.info("item revealed: ${item.revealed}")
            // logger.info("item credentialInfo: ${item.credentialInfo.toJsonElement()}")
        }

        val selectedCredentials: AnonCredsSelectedCredentials =
            anonCredsFormat ?: anonCredsSelected

        // logger.info("selectedCredentials: $selectedCredentials")

        val format = ProofFormatSpec(
            format = ANONCREDS_PRESENTATION,
            attachmentId = attachmentId,
        )

        val proof = createProof(requestMessage, requestJson, anonCredsSelected, proofFormats)

        val anonCredsProofJsonElement = Json.encodeToJsonElement(
            serializer<AnonCredsProof>(),
            proof,
        )

        // logger.info("anonCredsProofjsonElement: $anonCredsProofJsonElement")

        val attachment = Attachment(
            id = attachmentId,
            mimetype = "application/json",
            data = AttachmentData(
                base64 = JsonEncoder.toBase64(anonCredsProofJsonElement),
            ),
        )

        return ProofFormatCreateReturn(
            attachment = attachment,
            format = format,
        )
    }

    private suspend fun validateCredentialChosen(
        chosenCredentialId: String?,
        proofRequest: AnonCredsProofRequest
    ) {
        require(!chosenCredentialId.isNullOrBlank()) {
            "No credential was selected."
        }

        val credential: CredentialExchangeRecord =
            agent.credentialExchangeRepository.getByW3cCredentialId(chosenCredentialId)

        val recordAttrs = credential.credentialAttributes
            ?.associate { it.name to it.value }
            ?: emptyMap()

        val credDefId = credential.credentialDefinitionId

        val requestedAttrNames: Set<String> =
            proofRequest.requestedAttributes.values
                .flatMap { it.names ?: emptyList() }
                .toSet()

        val missingAttrs = requestedAttrNames.filter { !recordAttrs.containsKey(it) }

        if (missingAttrs.isNotEmpty()) {
            throw IllegalArgumentException(
                "The selected credential does not contain the required attributes: $missingAttrs"
            )
        }

        val requestedCredDefIds = proofRequest.requestedAttributes.values
            .mapNotNull { it.restrictions?.firstOrNull()?.credDefId }
            .toSet()

        if (requestedCredDefIds.isNotEmpty() && credDefId !in requestedCredDefIds) {
            throw IllegalArgumentException(
                "The selected credential has an unexpected credentialDefinitionId. " +
                        "Expected: $requestedCredDefIds | Found: $credDefId"
            )
        }

        val predicates = proofRequest.requestedPredicates.values

        predicates.forEach { predicate ->
            val attrName = predicate.name
            val rawValue = recordAttrs[attrName]
                ?: throw IllegalArgumentException(
                    "The credential does not contain the required attribute for predicate: $attrName"
                )

            val attrValue = rawValue.toIntOrNull()
                ?: throw IllegalArgumentException(
                    "The attribute '$attrName' is not numeric, making predicate validation impossible."
                )

            val satisfied = when (predicate.pType) {
                PredicateType.GreaterThanOrEqualTo -> attrValue >= predicate.pValue
                PredicateType.GreaterThan -> attrValue > predicate.pValue
                PredicateType.LessThanOrEqualTo -> attrValue <= predicate.pValue
                PredicateType.LessThan -> attrValue < predicate.pValue
                else -> false
            }

            if (!satisfied) {
                throw IllegalArgumentException(
                    "Predicate failed for attribute '$attrName'. " +
                            "Value: $attrValue | Rule: ${predicate.pType} ${predicate.pValue}"
                )
            }
        }
    }

    override suspend fun processPresentation(
        requestAttachment: Attachment,
        presentationAttachment: Attachment,
        proofRecord: ProofExchangeRecord,
        presentationMessage: PresentationMessageV2,
        requestMessage: RequestPresentationMessageV2,
    ): Boolean {

        val requestJson: AnonCredsProofRequest =
            Json.decodeFromString(requestAttachment.getDataAsJson())

        // logger.info("request json: $requestJson")

        val anonCredsProof: AnonCredsProof =
            Json.decodeFromString(presentationAttachment.getDataAsJson())

        // logger.info("anonCredsProof: $anonCredsProof")

        for ((_, attribute) in anonCredsProof.requestedProof.revealedAttrs) {
            if (!checkValidCredentialValueEncoding(attribute.raw, attribute.encoded)) {
                throw CredoError(
                    "Invalid encoded value for attribute. Raw='${attribute.raw}', Expected='${AnonCredsEncoder.encodeCredentialValue(attribute.raw)}', Actual='${attribute.encoded}'"
                )
            }
        }

        for ((_, group) in anonCredsProof.requestedProof.revealedAttrGroups.orEmpty()) {
            for ((attributeName, attribute) in group.values) {
                if (!checkValidCredentialValueEncoding(attribute.raw, attribute.encoded)) {
                    throw CredoError(
                        "Invalid encoded value for attribute '$attributeName'. Raw='${attribute.raw}', " +
                                "Expected='${AnonCredsEncoder.encodeCredentialValue(attribute.raw)}', Actual='${attribute.encoded}'"
                    )
                }
            }
        }

        val schemasMap: Map<String, AnonCredsSchema> =
            agent.ledgerService.getSchemas(anonCredsProof.identifiers.map { it.schemaId }.toSet())

        val schemas = AnonCredsSchemas(schemasMap)

        // logger.info("schemas: $schemas")

        val credentialDefinitionsMap: Map<String, AnonCredsCredentialDefinition> =
            ProofUtils.getCredentialDefinitions(
                agent,
                anonCredsProof.identifiers.map { it.credDefId }.toSet()
            )

        // logger.info("credentialDefinitionsMap: $credentialDefinitionsMap")

        val credentialDefinitionUniffiMap: Map<String, CredentialDefinition> =
            convert(credentialDefinitionsMap)

        // logger.info("credentialDefinitionUniffiMap: $credentialDefinitionUniffiMap")

        val revocationRegistries =
            RevocationRegistries(agent).getRevocationRegistriesForProof(anonCredsProof)

        // logger.info("revocationRegistries: $revocationRegistries")

        val anonCredsCredentialDefinitions = AnonCredsCredentialDefinitions(
            credentialDefinitions = credentialDefinitionsMap
        )

        // logger.info("anonCredsCredentialDefinitions: $anonCredsCredentialDefinitions")

        proofRecord.isVerified = agent.anoncredsVerifierService.verifyProof(
            options = VerifyProofOptions(
                proofRequest = requestJson,
                proof = anonCredsProof,
                presentationMessage = presentationMessage,
                schemas = schemas,
                credentialDefinitions = anonCredsCredentialDefinitions,
                revocationRegistries = revocationRegistries,
                requestMessage = requestMessage,
            )
        )

        // logger.info("is verified: ${proofRecord.isVerified}")

        return proofRecord.isVerified!!
    }

    suspend fun convert(
        input: Map<String, AnonCredsCredentialDefinition>,
    ): Map<String, CredentialDefinition> {
        return input.mapValues { (credDefId, _) ->
            val credDef = agent.ledgerService.getCredentialDefinition(credDefId)
            CredentialDefinition(credDef)
        }
    }

    suspend fun getRevocationRegistryDefinitions(
        revocationRegistryIds: Set<String>
    ): Map<String, RevocationRegistryDefinition> {
        val registryDefinitions = mutableMapOf<String, RevocationRegistryDefinition>()
        val lock = Mutex()

        revocationRegistryIds.concurrentForEach { registryId ->
            val definitionJson = agent.ledgerService.getRevocationRegistryDefinition(registryId)
            lock.withLock {
                registryDefinitions[registryId] =
                    RevocationRegistryDefinition(definitionJson)
            }
        }

        return registryDefinitions
    }

    override suspend fun getCredentialsForRequest(
        proofRecord: ProofExchangeRecord,
        proofFormats: Map<String, JsonElement>?,
        requestAttachment: Attachment,
        proposalAttachment: Attachment?,
    ): AnonCredsCredentialsForProofRequest {

        val proofRequestJson: AnonCredsProofRequest =
            Json.decodeFromString(requestAttachment.getDataAsJson())

        val format =
            FormatGeneric.getAnonCredsFormatGeneric<AnonCredsSelectedCredentials>(proofFormats)

        return GetCredentialsForProofRequestReferent.getCredentialsForAnonCredsProofRequest(
            agent = agent,
            proofRequest = proofRequestJson,
            options = AnonCredsGetCredentialsForProofRequestOptions(
                filterByNonRevocationRequirements = true
            )
        )
    }

    override suspend fun selectCredentialsForRequest(
        proofRecord: ProofExchangeRecord,
        proofFormats: Map<String, JsonElement>?,
        requestAttachment: Attachment,
        proposalAttachment: Attachment?,
    ): AnonCredsSelectedCredentials {

        val proofRequestJson: AnonCredsProofRequest =
            Json.decodeFromString(requestAttachment.getDataAsJson())

        val format =
            FormatGeneric.getAnonCredsFormatGeneric<AnonCredsSelectedCredentials>(proofFormats)

        return _selectCredentialsForRequest(
            proofRequest = proofRequestJson,
            options = AnonCredsGetCredentialsForProofRequestOptions(
                filterByNonRevocationRequirements = true
            )
        )
    }

    override suspend fun shouldAutoRespondToProposal(
        proofRecord: ProofExchangeRecord,
        proposalAttachment: Attachment,
        requestAttachment: Attachment,
    ): Boolean {

        val proposalJson: AnonCredsProofRequest =
            Json.decodeFromString(proposalAttachment.getDataAsJson())

        val requestJson: AnonCredsProofRequest =
            Json.decodeFromString(requestAttachment.getDataAsJson())

        val equal =
            RequestsEquals.areAnonCredsProofRequestsEqual(proposalJson, requestJson)

        // logger.debug("Proposal equals request: $equal")
        return equal
    }

    override suspend fun shouldAutoRespondToRequest(
        proofRecord: ProofExchangeRecord,
        requestAttachment: Attachment,
        proposalAttachment: Attachment,
    ): Boolean {

        val proposalJson: AnonCredsProofRequest =
            Json.decodeFromString(proposalAttachment.getDataAsJson())

        val requestJson: AnonCredsProofRequest =
            Json.decodeFromString(requestAttachment.getDataAsJson())

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
        return formatIdentifier in listOf(
            ANONCREDS_PRESENTATION_PROPOSAL,
            ANONCREDS_PRESENTATION_REQUEST,
            ANONCREDS_PRESENTATION
        )
    }

    private fun createRequestFromPreview(
        anoncredsFormat: AnonCredsProposeProofFormat,
    ): AnonCredsProofRequest {
        val requestedAttributes: Map<String, AnonCredsRequestedAttribute> = anoncredsFormat.requestedAttributes ?: emptyMap()
        val requestedPredicates: Map<String, AnonCredsRequestedPredicate> = anoncredsFormat.requestedPredicates ?: emptyMap()

        return AnonCredsProofRequest(
            name = anoncredsFormat.name ?: "Proof request",
            version = anoncredsFormat.version ?: "1.0",
            nonce = agent.anonCredsHolderService.generateNonce(),
            requestedAttributes = requestedAttributes,
            requestedPredicates = requestedPredicates,
            nonRevoked = anoncredsFormat.nonRevokedInterval
        )
    }

    private fun createRequestFromPreview(
        name: String,
        version: String,
        nonce: String,
        attributes: List<AnonCredsPresentationPreviewAttribute> = emptyList(),
        predicates: List<AnonCredsPresentationPreviewPredicate> = emptyList(),
        nonRevokedInterval: AnonCredsNonRevokedInterval? = null,
    ): AnonCredsProofRequest {

        val groupedAttributes =
            mutableMapOf<String, MutableList<AnonCredsPresentationPreviewAttribute>>()

        for (attr in attributes) {
            val referent = attr.referent ?: attr.name
            groupedAttributes.getOrPut(referent) { mutableListOf() }.add(attr)
        }

        val requestedAttributes = mutableMapOf<String, AnonCredsRequestedAttribute>()

        for ((referent, props) in groupedAttributes) {
            val singleName: String? = if (props.size == 1) props[0].name else null
            val nameList: List<String>? = if (props.size > 1) props.map { it.name } else null

            requestedAttributes[referent] = AnonCredsRequestedAttribute(
                name = singleName,
                names = nameList,
                restrictions = listOf(
                    AnonCredsProofRequestRestriction(
                        credDefId = props.first().credentialDefinitionId
                    )
                ),
                nonRevoked = nonRevokedInterval
            )
        }

        val requestedPredicates = mutableMapOf<String, AnonCredsRequestedPredicate>()

        for (pred in predicates) {
            requestedPredicates[pred.name] = AnonCredsRequestedPredicate(
                name = pred.name,
                pType = PredicateType.fromString(pred.predicateType),
                pValue = pred.threshold,
                restrictions = listOf(
                    AnonCredsProofRequestRestriction(
                        credDefId = pred.credentialDefinitionId
                    )
                ),
                nonRevoked = nonRevokedInterval
            )
        }

        return AnonCredsProofRequest(
            name = name,
            version = version,
            nonce = nonce,
            requestedAttributes = requestedAttributes,
            requestedPredicates = requestedPredicates,
            nonRevoked = nonRevokedInterval
        )
    }

    private fun getFormatData(data: Any, id: String): Attachment {
        return Attachment(
            id = id,
            mimetype = "application/json",
            data = AttachmentData(
                base64 = JsonEncoder.toBase64(data)
            )
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
                options = options
            )

        val selectedAttributes = mutableMapOf<String, AnonCredsRequestedAttributeMatch>()
        val selectedPredicates = mutableMapOf<String, AnonCredsRequestedPredicateMatch>()

        for ((name, matches) in credentialsForRequest.attributes) {
            val selected = matches.firstOrNull()
                ?: throw CredoError("Unable to automatically select requested attributes.")
            selectedAttributes[name] = selected
        }

        for ((name, matches) in credentialsForRequest.predicates) {
            val selected = matches.firstOrNull()
                ?: throw CredoError("Unable to automatically select requested predicates.")
            selectedPredicates[name] = selected
        }

        return AnonCredsSelectedCredentials(
            attributes = selectedAttributes,
            predicates = selectedPredicates,
            selfAttestedAttributes = emptyMap()
        )
    }

    suspend fun createProof(
        requestMessage: RequestPresentationMessageV2,
        proofRequest: AnonCredsProofRequest,
        selectedCredentials: AnonCredsSelectedCredentials,
        proofFormats: Map<String, JsonElement>?,
    ): AnonCredsProof = coroutineScope {

        val selectedEntries = buildList {
            addAll(selectedCredentials.attributes.values)
            addAll(selectedCredentials.predicates.values)
        }

        // logger.info("selectedEntries: $selectedEntries")

        val credentialObjects = selectedEntries.map { entry ->
            async {
                val id = when (entry) {
                    is AnonCredsRequestedAttributeMatch -> entry.credentialId
                    is AnonCredsRequestedPredicateMatch -> entry.credentialId
                    else -> error("Unexpected type: ${entry::class}")
                }

                agent.anonCredsHolderService.getCredential(
                    credentialId = id,
                    useUnqualifiedIdentifiersIfPresent =
                        ProofRequestOperations.proofRequestUsesUnqualifiedIdentifiers(proofRequest)
                )
            }
        }.awaitAll()

        val schemaIds = credentialObjects.map { it.schemaId }.toSet()
        val credDefIds = credentialObjects.map { it.credentialDefinitionId }.toSet()

        val schemas = ProofUtils.getSchemas(agent, schemaIds)
        val credentialDefinitions = ProofUtils.getCredentialDefinitions(agent, credDefIds)

        val revocationData =
            RevocationRegistries(agent).getRevocationRegistriesForRequest(
                proofRequest,
                selectedCredentials
            )

        val updatedSelected = revocationData.updatedSelectedCredentials
        val revocationRegistries = revocationData.revocationRegistries

        val anonCredsRevocationRegistries = mutableMapOf<String, AnonCredsRevocationRegistryEntry>()

        revocationRegistries.mapValues { (key, value) ->
            val revRegValue =
                Json.decodeFromString<RevocationRegistryValue>(value.definition.value.toString())

            val anonDef = AnonCredsRevocationRegistryDefinition(
                issuerId = value.definition.issuerId,
                revocDefType = value.definition.revocDefType,
                credDefId = value.definition.credDefId,
                tag = value.definition.tag,
                value = revRegValue
            )

            val statusLists =
                value.revocationStatusLists?.mapValues { (_, v) ->
                    AnonCredsRevocationStatusList.toAnonCreds(v)
                }?.toMutableMap() ?: mutableMapOf()

            anonCredsRevocationRegistries[key] = AnonCredsRevocationRegistryEntry(
                tailsFilePath = agent.ledgerService.getTailsPath(),
                tailsHash = value.tailsHash,
                definition = anonDef,
                revocationStatusLists = statusLists
            )
        }

        val anonSchemas = AnonCredsSchemas(schemas)
        val anonCredDefs = AnonCredsCredentialDefinitions(credentialDefinitions)

        agent.anonCredsHolderService.createProof(
            options = CreateProofOptions(
                requestMessage = requestMessage,
                proofRequest = proofRequest,
                selectedCredentials = updatedSelected,
                schemas = anonSchemas,
                credentialDefinitions = anonCredDefs,
                revocationRegistries = anonCredsRevocationRegistries,
                proofFormats = proofFormats
            )
        )
    }

    private fun checkValidCredentialValueEncoding(raw: Any, encoded: String): Boolean {
        return encoded == AnonCredsEncoder.encodeCredentialValue(raw)
    }

}





