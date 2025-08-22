package org.hyperledger.ariesframework.anoncreds.formats

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchemas
import org.hyperledger.ariesframework.anoncreds.model.RevocationRegistriesForRequestResult
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateProofOptions
import org.hyperledger.ariesframework.anoncreds.utils.AnonCredsEncoder
import org.hyperledger.ariesframework.credentials.utils.JsonEncoder
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.proofs.formats.ProofFormatService
import org.hyperledger.ariesframework.proofs.models.ProofFormatCreateReturn
import org.hyperledger.ariesframework.proofs.models.ProofFormatProcessOptions
import org.hyperledger.ariesframework.proofs.models.ProofFormatSpec
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.proofs.utils.RequestsEquals
import org.hyperledger.ariesframework.proofs.verifier.VerifyProofOptions
import org.slf4j.LoggerFactory
import java.util.UUID

import anoncreds_uniffi.W3cProcess

class AnoncredsProofFormatService (
    override val formatKey: String = "anoncreds",
    val agent: Agent
) : ProofFormatService<AnoncredsProofFormat> {

    private val logger = LoggerFactory.getLogger(AnoncredsCredentialFormatService::class.java)

    companion object {
        const val ANONCREDS_PRESENTATION_PROPOSAL = "anoncreds/proof-request@v1.0"
        const val ANONCREDS_PRESENTATION_REQUEST = "anoncreds/proof-request@v1.0"
        const val ANONCREDS_PRESENTATION = "anoncreds/proof@v1.0"
    }

    override suspend fun createProposal(profRecord: ProofExchangeRecord, attachmentId: String?, proofFormats: Map<String, JsonElement>): ProofFormatCreateReturn {
        val format = ProofFormatSpec(
            attachmentId = attachmentId!!,
            format = ANONCREDS_PRESENTATION_PROPOSAL
        )

        val anoncredsFormat = FormatGeneric.getAnonCredsFormatGeneric<AnonCredsProposeProofFormat>(proofFormats)

        val proofRequest = createRequestFromPreview(
            name= anoncredsFormat.name ?: "Proof request" ,
            version= anoncredsFormat.version ?: "1.0",
            nonce = agent.anonCredsHolderService.generateNonce(), //revisar implementacao
            attributes= anoncredsFormat.attributes ?: emptyList(),
            predicates= anoncredsFormat.predicates ?: emptyList(),
            nonRevokedInterval= anoncredsFormat.nonRevokedInterval,
        )

        val attachment = this.getFormatData(proofRequest, format.attachmentId)

        return ProofFormatCreateReturn(
            format = format,
            attachment = attachment
        )
    }

    override suspend fun processProposal(attachment: Attachment, proofRecord: ProofExchangeRecord) {

        val json = attachment.getDataAsJson()
        val proposalJson : AnonCredsProofRequest = Json.decodeFromString<AnonCredsProofRequest>(attachment.getDataAsJson())
        DuplicateNames.assertNoDuplicateGroupsNamesInProofRequest(proposalJson)
    }

    override suspend fun acceptProposal(
        proofRecord: ProofExchangeRecord,
        attachmentId: String?,
        proposalAttachment: Attachment,
        proofFormats: Map<String, JsonElement>?
    ): ProofFormatCreateReturn {
        val format = ProofFormatSpec(
            attachmentId = attachmentId!!,
            format = ANONCREDS_PRESENTATION_REQUEST
        )
        val proposalJson : AnonCredsProofRequest = Json.decodeFromString<AnonCredsProofRequest>(proposalAttachment.getDataAsJson())

        val request = proposalJson.copy(
            nonce = agent.anonCredsHolderService.generateNonce()
        )

        val attachment = this.getFormatData(request, format.attachmentId)

        return ProofFormatCreateReturn(
            format = format,
            attachment = attachment
        )

    }

    override suspend fun createRequest(
        proofRecord: ProofExchangeRecord,
        attachmentId: String?,
        proofFormats: Map<String, JsonElement>?
    ): ProofFormatCreateReturn {

        val format = ProofFormatSpec(
            format = ANONCREDS_PRESENTATION_REQUEST,
            attachmentId = attachmentId!!
        )

        val anoncredsFormat = FormatGeneric.getAnonCredsFormatGeneric<AnonCredsProposeProofFormat>(proofFormats)

        val request : AnonCredsProofRequest = createRequestFromPreview(
            name= anoncredsFormat.name ?: "Proof request" , //else eu coloquei
            version= anoncredsFormat.version ?: "1.0", //else eu coloquei
            nonce = agent.anonCredsHolderService.generateNonce(), //revisar implementacao
            attributes= anoncredsFormat.attributes ?: emptyList(),
            predicates= anoncredsFormat.predicates ?: emptyList(),
            nonRevokedInterval= anoncredsFormat.nonRevokedInterval,
        )

        // Assert attribute and predicate (group) names do not match
        DuplicateNames.assertNoDuplicateGroupsNamesInProofRequest(request)

        val attachment = this.getFormatData(request, format.attachmentId)

        return ProofFormatCreateReturn(
            attachment = attachment,
            format = format
        )
    }

    override suspend fun processRequest(options: ProofFormatProcessOptions) {
        val attachment = options.attachment
        val requestJson : AnonCredsProofRequest = Json.decodeFromString<AnonCredsProofRequest>(attachment.getDataAsJson())
        DuplicateNames.assertNoDuplicateGroupsNamesInProofRequest(requestJson)
    }


    override suspend fun acceptRequest(proofRecord: ProofExchangeRecord,
                                       proofFormats: Map<String, JsonElement>?,
                                       attachmentId: String?,
                                       requestAttachment: Attachment,
                                       proposalAttachment: Attachment?
    ): ProofFormatCreateReturn {
        val format = ProofFormatSpec(
            format = ANONCREDS_PRESENTATION,
            attachmentId = attachmentId!!
        )

        val requestJson: AnonCredsProofRequest = Json.decodeFromString<AnonCredsProofRequest>(requestAttachment.getDataAsJson())

        val anoncredsFormat = FormatGeneric.getAnonCredsFormatGeneric<AnonCredsSelectedCredentials>(proofFormats)

        val anoncredsSelected : AnonCredsSelectedCredentials = _selectCredentialsForRequest(
            proofRequest = requestJson,
            options = AnonCredsGetCredentialsForProofRequestOptions(
                filterByNonRevocationRequirements = true
            )
        )

        val selectedCredentials: AnonCredsSelectedCredentials = anoncredsFormat ?: anoncredsSelected

        //val proof = createProof(requestJson, selectedCredentials)
        val attachment = getFormatData(id=format.attachmentId) //getFormatData(proof, format.attachmentId)

        return ProofFormatCreateReturn(
            attachment = attachment,
            format = format
        )
    }

    override suspend fun processPresentation(requestAttachment: Attachment,
                                             attachment: Attachment,
                                             proofRecord: ProofExchangeRecord): Boolean {

        val requestJson: AnonCredsProofRequest = Json.decodeFromString<AnonCredsProofRequest>(requestAttachment.getDataAsJson())
        val anonCredsProof: AnonCredsProof = Json.decodeFromString<AnonCredsProof>(attachment.getDataAsJson())

        for ((referent, attribute) in anonCredsProof.requestedProof.revealedAttrs) {
            if (!checkValidCredentialValueEncoding(attribute.raw, attribute.encoded)) {
                throw CredoError(
                    "The encoded value for '$referent' is invalid. " +
                            "Expected '${AnonCredsEncoder.encodeCredentialValue(attribute.raw)}'. " +
                            "Actual '${attribute.encoded}'"
                )
            }
        }

        for ((_, attributeGroup) in anonCredsProof.requestedProof.revealedAttrGroups.orEmpty()) {
            for ((attributeName, attribute) in attributeGroup.values) {
                if (!checkValidCredentialValueEncoding(attribute.raw, attribute.encoded)) {
                    throw CredoError(
                        "The encoded value for '$attributeName' is invalid. " +
                                "Expected '${AnonCredsEncoder.encodeCredentialValue(attribute.raw)}'. " +
                                "Actual '${attribute.encoded}'"
                    )
                }
            }
        }

        val schemasMap: Map<String, AnonCredsSchema> =
            agent.ledgerService.getSchemas(anonCredsProof.identifiers.map { it.schemaId }.toSet())
        val schemas = AnonCredsSchemas(schemasMap)

        val credentialDefinitionsMap: Map<String, AnonCredsCredentialDefinition> =
            getCredentialDefinitions(anonCredsProof.identifiers.map { it.credDefId }.toSet())
        val credentialsDefinitions = AnonCredsCredentialDefinitions(credentialDefinitionsMap)

        //val revocationRegistries = RevocationRegistries(agent).getRevocationRegistriesForProof(anonCredsProof)

        // Verificar prova
//        val verified = agent.anoncredsVerifierService.verifyProof(
//            options = VerifyProofOptions(
//                proofRequest = requestJson,
//                proof = anonCredsProof,
//                schemas = schemas,
//                credentialDefinitions = credentialsDefinitions,
//                revocationRegistries = revocationRegistries
//            )
//        )

        return false

    }

    override suspend fun getCredentialsForRequest(
        proofRecord: ProofExchangeRecord,
        proofFormats: Map<String, JsonElement>?,
        requestAttachment: Attachment,
        proposalAttachment: Attachment?
    ): AnonCredsCredentialsForProofRequest {
        val proofRequestJson : AnonCredsProofRequest = Json.decodeFromString<AnonCredsProofRequest>(requestAttachment.getDataAsJson())

        //TODO se o valor de filterByNonRevocationRequirements for false vem ele, senao por padrao é true, achar dentro
        // um filterByNonRevocationRequirements
        val anoncredsFormat = FormatGeneric.getAnonCredsFormatGeneric<AnonCredsSelectedCredentials>(proofFormats)

        val anonCredsCredentialsForProofRequest : AnonCredsCredentialsForProofRequest = GetCredentialsForProofRequestReferent.getCredentialsForAnonCredsProofRequest(
            agent = agent,
            proofRequest = proofRequestJson,
            options = AnonCredsGetCredentialsForProofRequestOptions(
                filterByNonRevocationRequirements = true
            )
        )

        return anonCredsCredentialsForProofRequest
    }

    override suspend fun selectCredentialsForRequest(
        proofRecord: ProofExchangeRecord,
        proofFormats: Map<String, JsonElement>?,
        requestAttachment: Attachment,
        proposalAttachment: Attachment?
    ): AnonCredsSelectedCredentials {
        val proofRequestJson : AnonCredsProofRequest = Json.decodeFromString<AnonCredsProofRequest>(requestAttachment.getDataAsJson())

        //TODO se o valor de filterByNonRevocationRequirements for false vem ele, senao por padrao é true, achar dentro
        // um filterByNonRevocationRequirements
        val anoncredsFormat = FormatGeneric.getAnonCredsFormatGeneric<AnonCredsSelectedCredentials>(proofFormats)

        val selectedCredentials : AnonCredsSelectedCredentials = _selectCredentialsForRequest(
            proofRequest = proofRequestJson,
            options = AnonCredsGetCredentialsForProofRequestOptions(
                filterByNonRevocationRequirements = true
            )
        )

        return selectedCredentials
    }

    override suspend fun shouldAutoRespondToProposal(
        proofRecord: ProofExchangeRecord,
        proposalAttachment: Attachment,
        requestAttachment: Attachment
    ): Boolean {
        val proposalJson : AnonCredsProofRequest = Json.decodeFromString<AnonCredsProofRequest>(proposalAttachment.getDataAsJson())
        val requestJson : AnonCredsProofRequest = Json.decodeFromString<AnonCredsProofRequest>(requestAttachment.getDataAsJson())

        val areRequestsEquals = RequestsEquals.areAnonCredsProofRequestsEqual(proposalJson, requestJson)

        logger.debug("AnonCreds request and proposal are are equal: ${areRequestsEquals} > proposal: ${proposalJson} || request: ${requestJson}")
        return areRequestsEquals

    }

    override suspend fun shouldAutoRespondToRequest(
        proofRecord: ProofExchangeRecord,
        requestAttachment: Attachment,
        proposalAttachment: Attachment
    ): Boolean {
        val proposalJson : AnonCredsProofRequest = Json.decodeFromString<AnonCredsProofRequest>(proposalAttachment.getDataAsJson())
        val requestJson : AnonCredsProofRequest = Json.decodeFromString<AnonCredsProofRequest>(requestAttachment.getDataAsJson())

        return RequestsEquals.areAnonCredsProofRequestsEqual(proposalJson, requestJson)
    }

    override suspend fun shouldAutoRespondToPresentation(
        proofRecord: ProofExchangeRecord,
        proposalAttachment: Attachment?,
        requestAttachment: Attachment,
        presentationAttachment: Attachment
    ): Boolean {
        return true
    }

    override fun supportsFormat(formatIdentifier: String): Boolean {
        val supportedFormats = listOf(
            ANONCREDS_PRESENTATION_PROPOSAL,
            ANONCREDS_PRESENTATION_REQUEST,
            ANONCREDS_PRESENTATION
        )
        return supportedFormats.contains(formatIdentifier)
    }

    private fun createRequestFromPreview(
        name: String,
        version: String,
        nonce: String,
        attributes: List<AnonCredsPresentationPreviewAttribute> = emptyList(),
        predicates: List<AnonCredsPresentationPreviewPredicate> = emptyList(),
        nonRevokedInterval: AnonCredsNonRevokedInterval? = null
    ): AnonCredsProofRequest {

        // Agrupa atributos por referent (gerando um se faltar)
        val attributesByReferent = mutableMapOf<String, MutableList<AnonCredsPresentationPreviewAttribute>>()
        for (attr in attributes) {
            val referent = attr.referent ?: UUID.randomUUID().toString()
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
                        credDefId = props.first().credentialDefinitionId
                    )
                ),
                nonRevoked = null
            )
        }

        // Converte predicados pro requestedPredicates
        val requestedPredicates = mutableMapOf<String, AnonCredsRequestedPredicate>()
        for (pred in predicates) {
            requestedPredicates[UUID.randomUUID().toString()] = AnonCredsRequestedPredicate(
                name = pred.name,
                pType = pred.predicate,
                pValue = pred.threshold, // ajuste para .toLong() se seu tipo for Long
                restrictions = listOf(
                    AnonCredsProofRequestRestriction(
                        credDefId = pred.credentialDefinitionId
                    )
                ),
                nonRevoked = null
            )
        }

        // Monta o objeto final
        return AnonCredsProofRequest(
            name = name,
            version = version,
            nonce = nonce,
            requestedAttributes = requestedAttributes,
            requestedPredicates = requestedPredicates,
            nonRevoked = nonRevokedInterval
        )
    }

    private fun getFormatData(data: Any? = null, id: String): Attachment {
        return Attachment(
            id = id,
            mimetype = "application/json",
            data = AttachmentData(
                base64 = JsonEncoder.toBase64(data!!)
            )
        )
    }

    private suspend fun _selectCredentialsForRequest(
        proofRequest: AnonCredsProofRequest,
        options: AnonCredsGetCredentialsForProofRequestOptions
    ): AnonCredsSelectedCredentials {
        val credentialsForRequest = GetCredentialsForProofRequestReferent.getCredentialsForAnonCredsProofRequest(
            agent = agent,
            proofRequest = proofRequest,
            options = options
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
            selfAttestedAttributes = emptyMap()
        )
    }


//    suspend fun createProof(
//        proofRequest: AnonCredsProofRequest,
//        selectedCredentials: AnonCredsSelectedCredentials
//    ): AnonCredsProof = coroutineScope {
//        // attributes.values + predicates.values TODO
//        val selectedEntries = buildList {
//            addAll(selectedCredentials.attributes.values)
//            addAll(selectedCredentials.predicates.values)
//        }
//
//        // Paraleliza como Promise.all
//        val credentialObjects = selectedEntries.map { c ->
//            async {
//                agent.anonCredsHolderService.getCredential(
//                    credentialId = c.credentialId,
//                    useUnqualifiedIdentifiersIfPresent = ProofRequest.proofRequestUsesUnqualifiedIdentifiers(
//                        proofRequest
//                    )
//                )
//            }
//        }.awaitAll()
//
//        val schemasMap: Map<String, AnonCredsSchema> = agent.ledgerService.getSchemas(credentialObjects.map { it.schemaId }.toSet())
//        val schemas = AnonCredsSchemas(schemasMap)
//
//        val credentialDefinitionsMap: Map<String, AnonCredsCredentialDefinition> =
//            getCredentialDefinitions(credentialObjects.map { it.credentialDefinitionId }.toSet())
//        val credentialDefinitions = AnonCredsCredentialDefinitions(credentialDefinitionsMap)
//
//        // Revocation registries + selectedCredentials atualizados
//        val revocationRegistriesForRequestResult : RevocationRegistriesForRequestResult =
//            RevocationRegistries(agent).getRevocationRegistriesForRequest(proofRequest, selectedCredentials)
//
//        // Criar a prova
//        agent.anonCredsHolderService.createProof(
//            options = CreateProofOptions(
//                proofRequest = proofRequest,
//                selectedCredentials = revocationRegistriesForRequestResult.updatedSelectedCredentials,
//                schemas = schemas,
//                credentialDefinitions = credentialDefinitions,
//                revocationRegistries = revocationRegistriesForRequestResult.revocationRegistries
//            )
//        )
//    }

    private suspend fun getCredentialDefinitions(
        credentialDefinitionIds: Set<String>
    ): Map<String, AnonCredsCredentialDefinition> {
        val credentialDefinitions = mutableMapOf<String, AnonCredsCredentialDefinition>()
        for (credDefId in credentialDefinitionIds) {
            val credentialDefinitionResult =
                agent.ledgerService.getCredentialDefinition(credDefId)
            val anonCredsCredentialDefinition: AnonCredsCredentialDefinition = Json.decodeFromString(credentialDefinitionResult)
            credentialDefinitions[credDefId] = anonCredsCredentialDefinition
        }
        return credentialDefinitions
    }

    private fun checkValidCredentialValueEncoding(raw: Any, encoded: String) : Boolean {
        return encoded === AnonCredsEncoder.encodeCredentialValue(raw)
    }

}