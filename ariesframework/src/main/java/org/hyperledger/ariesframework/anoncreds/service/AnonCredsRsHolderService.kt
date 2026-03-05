package org.hyperledger.ariesframework.anoncreds.service

import anoncreds_uniffi.Credential
import anoncreds_uniffi.CredentialConversions
import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.CredentialOffer
import anoncreds_uniffi.CredentialRequestMetadata
import anoncreds_uniffi.CredentialRequestTuple
import anoncreds_uniffi.CredentialRevocationState
import anoncreds_uniffi.PresentationRequest
import anoncreds_uniffi.Prover
import anoncreds_uniffi.RequestedCredential
import anoncreds_uniffi.RevocationRegistryDefinition
import anoncreds_uniffi.RevocationStatusList
import anoncreds_uniffi.Schema
import anoncreds_uniffi.Verifier
import anoncreds_uniffi.W3cCredential
import anoncreds_uniffi.W3cProcess
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.Gson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.exception.AnonCredsRsError
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsRequestedAttributeMatch
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsRequestedPredicateMatch
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.GetCredentialsForProofRequestOptions
import org.hyperledger.ariesframework.anoncreds.formats.model.CredentialEntry
import org.hyperledger.ariesframework.anoncreds.formats.model.CredentialEntryResult
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredential
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialInfo
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequestMetadata
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProof
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequestRestriction
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedAttribute
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedPredicate
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.anoncreds.model.CreateCredentialRequestOptions
import org.hyperledger.ariesframework.anoncreds.model.LegacyToW3cCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.ProcessOptions
import org.hyperledger.ariesframework.anoncreds.model.RevocationRegistryValue
import org.hyperledger.ariesframework.anoncreds.model.StoreCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.StoreCredentialW3cOptions
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateCredentialRequestReturn
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateLinkSecretOptions
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateLinkSecretReturn
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateProofOptions
import org.hyperledger.ariesframework.anoncreds.model.holder.CredentialForProofRequest
import org.hyperledger.ariesframework.anoncreds.model.holder.GetCredentialsForProofRequestReturn
import org.hyperledger.ariesframework.anoncreds.model.holder.StoreLinkSecretOptions
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsCredentialRecord
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsLinkSecretRecord
import org.hyperledger.ariesframework.anoncreds.utils.Indyidentifiers
import org.hyperledger.ariesframework.credentials.formats.anoncreds.MetadataKeys
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.proofs.models.RequestedCredentialsAnoncreds
import org.hyperledger.ariesframework.proofs.utils.ProofRequestOperations
import org.hyperledger.ariesframework.proofs.utils.W3cUtils
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.toJsonString
import org.hyperledger.ariesframework.util.Base58
import org.hyperledger.ariesframework.util.JsonUtils.Companion.toJsonMap
import org.hyperledger.ariesframework.util.JsonUtils.Companion.toJsonString
import org.hyperledger.ariesframework.util.LogUtil
import org.hyperledger.ariesframework.util.PrintLongLine
import org.hyperledger.ariesframework.vc.model.ProcessCredentialOptions
import org.hyperledger.ariesframework.vc.model.W3cAnonCredsCredentialMetadata
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential
import org.hyperledger.ariesframework.vc.proof.CredentialProve
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRecord
import org.hyperledger.ariesframework.vc.util.W3cAnonCredsUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.math.max

class AnonCredsRsHolderService(val agent: Agent) : AnonCredsHolderService {

    private val logger = LoggerFactory.getLogger(AnonCredsRsHolderService::class.java)

    override suspend fun storeCredential(
        options: StoreCredentialOptions,
        metadata: Map<String, Any>?,
    ): String {
        val credential = options.credential
        val credentialDefinition = options.credentialDefinition
        val credentialDefinitionId = options.credentialDefinitionId
        val credentialRequestMetadata = options.credentialRequestMetadata
        val schema = options.schema
        val revocationRegistry = options.revocationRegistry

        // logger.info("credential: $credential")
        // logger.info("credentialDefinition: $credentialDefinition")
        // logger.info("credentialRequestMetadata: $credentialRequestMetadata")
        // logger.info("revocationRegistry: $revocationRegistry")

        var w3cJsonLdCredential: W3cJsonLdVerifiableCredential
        if (credential is W3cJsonLdVerifiableCredential) {
            w3cJsonLdCredential = credential
            // logger.info("w3cJsonLdCredential: $w3cJsonLdCredential")
        } else {
            val legacyToW3cCredentialOptions = LegacyToW3cCredentialOptions(
                credential = credential as AnonCredsCredential,
                issuerId = credentialDefinition.issuerId,
                processOptions = ProcessOptions(
                    credentialDefinition = credentialDefinition,
                    credentialRequestMetadata = credentialRequestMetadata,
                    revocationRegistryDefinition = revocationRegistry?.definition,
                ),
            )
            // logger.info("legacyToW3cCredentialOptions: $legacyToW3cCredentialOptions")

            w3cJsonLdCredential = legacyToW3cCredential(legacyToW3cCredentialOptions)
            // logger.info("w3cJsonLdCredential: $w3cJsonLdCredential")
        }

        val storeCredentialW3cOptions = StoreCredentialW3cOptions(
            credential = w3cJsonLdCredential,
            credentialDefinitionId = credentialDefinitionId,
            schema = schema,
            schemaId = options.schemaId,
            credentialDefinition = credentialDefinition,
            revocationRegistryDefinition = revocationRegistry?.definition,
            revocationRegistryId = revocationRegistry?.id,
            credentialRequestMetadata = credentialRequestMetadata,
        )
        val w3cCredentialRecord = storeW3cCredential(storeCredentialW3cOptions)
        // logger.info("w3cCredentialRecord: $w3cCredentialRecord")

        return w3cCredentialRecord.id
    }

    override suspend fun legacyToW3cCredential(options: LegacyToW3cCredentialOptions): W3cJsonLdVerifiableCredential {
        val anonCredsCredential: AnonCredsCredential = options.credential
        val issuerId: String = options.issuerId
        val processOptions: ProcessOptions? = options.processOptions

        // logger.info("anonCredsCredential: $anonCredsCredential")

        val anonCredsCredentialJson = Json.encodeToString(anonCredsCredential)
        PrintLongLine.print(anonCredsCredentialJson)

        var credential: Credential = Credential(anonCredsCredentialJson)
        // logger.info("credential: $credential")

        val credentialW3cStr: String =
            CredentialConversions().credentialToW3cJson(credential, issuerId, "1.1")
        logger.info("credentialW3cStr: $credentialW3cStr")

        val w3cCredential: W3cCredential = W3cCredential(credentialW3cStr)

        val w3cJsonLdVerifiableCredential: W3cJsonLdVerifiableCredential =
            convertToW3cJsonLd(w3cCredential, credentialW3cStr)

        var w3cJsonLdVC = w3cJsonLdVerifiableCredential
        if (processOptions != null) {
            // logger.info("processOptions: $processOptions")
            w3cJsonLdVC = processW3cCredential(w3cCredential, w3cJsonLdVerifiableCredential, processOptions)
        }

        return w3cJsonLdVC
    }

    override suspend fun createProof(options: CreateProofOptions): AnonCredsProof {
        val requestMessage = options.requestMessage
        val proofRequest = options.proofRequest
        val proofFormats = options.proofFormats
        val selectedCredentials = options.selectedCredentials
        val credentialDefinitions = options.credentialDefinitions
        val schemas = options.schemas

        // logger.error(">>> HOLDER nonce = ${requestMessage.toJsonString()}")

        val rsCredentialDefinitions = mutableMapOf<String, CredentialDefinition>()
        val rsSchemas = mutableMapOf<String, Schema>()
        val retrievedCredentials = mutableMapOf<String, Any>()

        credentialDefinitions.credentialDefinitions.forEach { (credDefId, credDef) ->
            val jsonString = credDef.toJson()
            rsCredentialDefinitions[credDefId] = CredentialDefinition(jsonString)
        }

        schemas.schemas.forEach { (schemaId, schema) ->
            val json = schema.toJson()
            rsSchemas[schemaId] = Schema(json)
        }

        val getCredentialId: (Any) -> String = { attribute ->
            when (attribute) {
                is AnonCredsRequestedAttributeMatch -> attribute.credentialId
                is AnonCredsRequestedPredicateMatch -> attribute.credentialId
                else -> error("Unexpected type: ${attribute::class}")
            }
        }

        val getTimestamp: (Any) -> ULong? = { attribute ->
            when (attribute) {
                is AnonCredsRequestedAttributeMatch -> attribute.timestamp
                is AnonCredsRequestedPredicateMatch -> attribute.timestamp
                else -> error("Unexpected type: ${attribute::class}")
            }
        }

        val credentialEntryFromAttribute: suspend (Any) -> CredentialEntryResult = { attribute ->

            val credentialId = getCredentialId(attribute)
            var record = retrievedCredentials[credentialId]

            if (record == null) {
                val w3c = agent.w3cCredentialRepository.findById(credentialId)
                if (w3c != null) {
                    retrievedCredentials[credentialId] = w3c
                    record = w3c
                } else {
                    val legacy = agent.anonCredsCredentialRepository.getByCredentialId(credentialId)
                    retrievedCredentials[credentialId] = legacy
                    record = legacy
                }
            }

            val proofUsesUnqualified =
                ProofRequestOperations.proofRequestUsesUnqualifiedIdentifiers(proofRequest)

            val info = getAnoncredsCredentialInfoFromRecord(
                record!!,
                proofUsesUnqualified,
            )

            val timestamp = getTimestamp(attribute)
            var revocationState: CredentialRevocationState? = null

            if (
                timestamp != null &&
                info.credentialRevocationId != null &&
                info.revocationRegistryId != null
            ) {
                val credentialRevocationId = info.credentialRevocationId!!
                val revocationRegistryId = info.revocationRegistryId!!

                val registryData = options.revocationRegistries[revocationRegistryId]
                    ?: throw AnonCredsRsError("Revocation Registry $revocationRegistryId not found")

                val definition: AnonCredsRevocationRegistryDefinition = registryData.definition
                val revocationStatusLists: MutableMap<ULong, AnonCredsRevocationStatusList> =
                    registryData.revocationStatusLists ?: error("revocationStatusLists missing")

                val statusList: AnonCredsRevocationStatusList = revocationStatusLists[timestamp]
                    ?: throw CredoError(
                        "Revocation status list for registry $revocationRegistryId and timestamp $timestamp not found",
                    )

                val valueJsonElement: JsonElement =
                    Json.encodeToJsonElement(RevocationRegistryValue.serializer(), definition.value)

                val valueJsonMap: Map<String, Any?> = valueJsonElement.toJsonMap()

                val jsonDict = mapOf(
                    "credDefId" to definition.credDefId,
                    "revRegDefId" to revocationRegistryId,
                    "tag" to definition.tag,
                    "value" to valueJsonMap,
                    "issuerId" to definition.issuerId,
                    "revocDefType" to definition.revocDefType,
                )

                val jsonString = jsonDict.toJsonString()
                val revocationRegistryDefinition =
                    RevocationRegistryDefinition(jsonString)

                val revocationStatusListJson = statusList.toJson()
                val statusListUniffi = RevocationStatusList(revocationStatusListJson)

                // logger.error(">>> HOLDER STATUS LIST (UNIFFI JSON)")
                // logger.info(" -> ${statusListUniffi.toJson()}")

                val tailsFile = File(registryData.tailsFilePath, registryData.tailsHash!!)
                if (!tailsFile.exists()) error("Tails file not found: ${tailsFile.path}")

                revocationState = Prover().createOrUpdateRevocationState(
                    revRegDef = revocationRegistryDefinition,
                    revStatusList = statusListUniffi,
                    revRegIdx = credentialRevocationId.toUInt(),
                    tailsPath = tailsFile.path,
                    revState = null,
                    oldRevStatusList = null,
                )
            }

            val credential = when (record) {
                is W3cCredentialRecord ->
                    W3cUtils.getCredentialUniffiByW3cCredentialRecord(record)

                is AnonCredsCredentialRecord ->
                    record.credential

                else -> error("Unexpected credential record type: ${record::class}")
            }

            var credJsonElement: JsonElement = if (credential is Credential) {
                Json.parseToJsonElement(credential.toJson())
            } else {
                credential as AnonCredsCredential
                Json.parseToJsonElement(credential.toJson())
            }

            var revocationStateJsonElement: JsonElement? = null
            if (revocationState != null) {
                revocationStateJsonElement = Json.parseToJsonElement(revocationState.toJson())
            }

            CredentialEntryResult(
                linkSecretId = info.linkSecretId,
                credentialEntry = CredentialEntry(
                    credential = credJsonElement,
                    timestamp = timestamp,
                    revocationState = revocationStateJsonElement,
                ),
                credentialId = credentialId,
            )
        }

        val requestedCredentials: RequestedCredentialsAnoncreds =
            RequestedCredentialsAnoncreds.mapToRequestedCredentialsWithKotlinx(proofFormats!!)

        val anoncredsCreds = mutableListOf<RequestedCredential>()
        val credentialIds = requestedCredentials.getCredentialIdentifiers()

        val schemaIds = mutableSetOf<String>()
        val credentialDefinitionIds = mutableSetOf<String>()

        credentialIds.map {
            val record = agent.w3cCredentialRepository.getById(it)
            val cred = W3cUtils.getCredentialUniffiByW3cCredentialRecord(record)

            schemaIds.add(cred.schemaId())
            credentialDefinitionIds.add(cred.credDefId())

            val requestedAttributes = mutableMapOf<String, Boolean>()
            val requestedPredicates = mutableListOf<String>()
            var timestamp: Int? = null
            requestedCredentials.requestedAttributes.forEach { (referent, attr) ->
                if (attr.credentialId == it) {
                    requestedAttributes[referent] = attr.revealed
                    if (attr.timestamp != null) {
                        timestamp = kotlin.math.max(attr.timestamp, timestamp ?: 0)
                    }
                }
            }
            requestedCredentials.requestedPredicates.forEach { (referent, pred) ->
                if (pred.credentialId == it) {
                    requestedPredicates.add(referent)
                    if (pred.timestamp != null) {
                        timestamp = kotlin.math.max(pred.timestamp, timestamp ?: 0)
                    }
                }
            }

            val revState =
                if (timestamp != null) {
                    agent.revocationService.createRevocationState(
                        cred,
                        timestamp!!.toInt(),
                    )
                } else {
                    null
                }

            anoncredsCreds.add(
                RequestedCredential(
                    cred = cred,
                    timestamp = timestamp?.toULong(),
                    revState = revState,
                    requestedAttributes = requestedAttributes,
                    requestedPredicates = requestedPredicates,
                ),
            )
        }

        val credentials = mutableListOf<CredentialEntryResult>()
        val credentialsProve = mutableListOf<CredentialProve>()

        var entryIndex = 0

        selectedCredentials.attributes.forEach { (referent, attribute) ->

            val existingIndex = credentials.indexOfFirst {
                it.credentialId == attribute.credentialId &&
                    it.credentialEntry.timestamp == attribute.timestamp
            }

            if (existingIndex >= 0) {
                credentialsProve.add(
                    CredentialProve(
                        entryIndex = existingIndex,
                        referent = referent,
                        isPredicate = false,
                        reveal = attribute.revealed,
                    ),
                )
            } else {
                val entry = credentialEntryFromAttribute(attribute)
                credentials.add(entry)

                credentialsProve.add(
                    CredentialProve(
                        entryIndex = entryIndex,
                        referent = referent,
                        isPredicate = false,
                        reveal = attribute.revealed,
                    ),
                )
                entryIndex++
            }
        }

        selectedCredentials.predicates.forEach { (referent, predicate) ->

            val existingIndex = credentials.indexOfFirst {
                it.credentialId == predicate.credentialId &&
                    it.credentialEntry.timestamp == predicate.timestamp
            }

            if (existingIndex >= 0) {
                credentialsProve.add(
                    CredentialProve(
                        entryIndex = existingIndex,
                        referent = referent,
                        isPredicate = true,
                        reveal = true,
                    ),
                )
            } else {
                val entry = credentialEntryFromAttribute(predicate)
                credentials.add(entry)

                credentialsProve.add(
                    CredentialProve(
                        entryIndex = entryIndex,
                        referent = referent,
                        isPredicate = true,
                        reveal = true,
                    ),
                )
                entryIndex++
            }
        }

        val linkSecretIds = credentials.map { it.linkSecretId }
        val linkSecretId = assertLinkSecretsMatch(linkSecretIds)
        val linkSecret = agent.anoncredsService.getLinkSecret(linkSecretId)

        val anoncredsRequest = requestMessage.anoncredsProofRequest()
        val presentationRequest = anoncreds_uniffi.PresentationRequest(anoncredsRequest)

        try {
            // logger.error("---- HOLDER createPresentation DEBUG ----")
            // logger.error("presentationRequest = ${presentationRequest.toJson()}")
            // logger.error("anoncredsCreds = $anoncredsCreds")
            // logger.error("schemas = $rsSchemas")
            // logger.error("credDefs = $rsCredentialDefinitions")
            // logger.error("-----------------------------------------")

            val presentation = Prover().createPresentation(
                presReq = presentationRequest,
                requestedCredentials = anoncredsCreds,
                selfAttestedAttributes = null,
                linkSecret = linkSecret,
                schemas = rsSchemas,
                credDefs = rsCredentialDefinitions,
            )

            val proofUniffi = presentation.proof()
            val aggr = proofUniffi.aggregatedProof

            // PrintLongLine.print("HOLDER PRESENTATION.PROOF- $proofUniffi")
            // PrintLongLine.print("HOLDER AGGREGATED- $aggr")

            val anonCredsProof: AnonCredsProof =
                Json.decodeFromString<AnonCredsProof>(presentation.toJson())

            return anonCredsProof
        } catch (e: Exception) {
            throw e
        }
    }

    private fun assertLinkSecretsMatch(linkSecretIds: List<String>): String {
        require(linkSecretIds.isNotEmpty()) {
            "No link secret ids provided"
        }

        val distinct = linkSecretIds.distinct()
        if (distinct.size > 1) {
            throw IllegalArgumentException("Multiple different link secret ids found: $linkSecretIds")
        }

        return distinct.first()
    }

    override suspend fun getCredentialsForProofRequest(options: GetCredentialsForProofRequestOptions): GetCredentialsForProofRequestReturn {
        val proofRequest = options.proofRequest
        val referent = options.attributeReferent

        val requestedAttribute = proofRequest.requestedAttributes[referent]
            ?: proofRequest.requestedPredicates[referent]?.asAnonCredsRequestedAttribute()
            ?: throw Exception("Referent not found in proof request")

        val useUnqualifiedIdentifiers =
            ProofRequestOperations.proofRequestUsesUnqualifiedIdentifiers(proofRequest)

        if (requestedAttribute.names == null && requestedAttribute.name == null) {
            throw Exception("Proof request attribute must have either name or names")
        }

        if (requestedAttribute.names != null && requestedAttribute.name != null) {
            throw Exception("Proof request attribute cannot have both name and names")
        }

        val attibutesList = mutableListOf<String>()
        val attributes = requestedAttribute.names ?: listOf(requestedAttribute.name!!)
        for (attribute in attributes) {
            attibutesList.add("anonCredsAttr::$attribute::value")
        }

        var tags = mutableMapOf<String, String>()
        if (requestedAttribute.restrictions != null) {
            tags = queryFromRestrictions(requestedAttribute.restrictions)
        }

        var credentials: List<W3cCredentialRecord> =
            agent.w3cCredentialRepository.findByQuery(tags.toJsonString())

        if (options.chosenCredentialId != null) {
            credentials = listOf(agent.w3cCredentialRepository.getById(options.chosenCredentialId))
        }

        val filteredCredentials = credentials.filter { rec ->
            attibutesList.all(rec.getTags()::containsKey)
        }

        val legacyCredentialWithMetadata: List<CredentialForProofRequest> =
            getLegacyCredentialsForProofRequest(options).credentials

        if (legacyCredentialWithMetadata.isNotEmpty()) {
            // logger.warn("Including legacy credentials in proof request.")
        }

        val credentialWithMetadata: List<CredentialForProofRequest> =
            filteredCredentials.map { credentialRecord ->
                CredentialForProofRequest(
                    credentialInfo = getAnoncredsCredentialInfoFromRecord(
                        credentialRecord,
                        useUnqualifiedIdentifiers,
                    ),
                    interval = proofRequest.nonRevoked,
                )
            }

        return GetCredentialsForProofRequestReturn(
            credentials = credentialWithMetadata + legacyCredentialWithMetadata,
        )
    }

    private fun convertToW3cJsonLd(
        w3cCredential: W3cCredential,
        credentialW3cStr: String,
    ): W3cJsonLdVerifiableCredential {
        // logger.info("w3cCredential: ${w3cCredential.toJson()}")

        val element =
            Json.parseToJsonElement(w3cCredential.toJson()).jsonObject.toMutableMap()
        // logger.info("element: $element")

        val subject = element["credentialSubject"]
        if (subject != null && subject !is JsonArray) {
            element["credentialSubject"] = JsonArray(listOf(subject))
        }

        val typeCred = element["type"]
        if (typeCred != null && typeCred !is JsonArray) {
            element["type"] = JsonArray(listOf(typeCred))
        }

        val normalized = JsonObject(element)
        // logger.info("normalized: $normalized")

        val w3cJsonLdVerifiableCredential: W3cJsonLdVerifiableCredential =
            W3cJsonLdVerifiableCredential.fromJson(normalized.toString())
        // logger.info("w3cJsonLdVerifiableCredential: $w3cJsonLdVerifiableCredential")

        return w3cJsonLdVerifiableCredential
    }

    private suspend fun processW3cCredential(
        w3cCredential: W3cCredential,
        crew3cJsonLdVC: W3cJsonLdVerifiableCredential,
        processOptions: ProcessOptions,
    ): W3cJsonLdVerifiableCredential {
        val mapper: ObjectMapper = jacksonObjectMapper()
        val (credentialDefinition, credentialRequestMetadata, revocationRegistryDefinition) =
            processOptions

        val processCredentialOptions = ProcessCredentialOptions(
            credentialRequestMetadata = credentialRequestMetadata,
            linkSecret = agent.wallet.linkSecretId!!,
            revocationRegistryDefinition = revocationRegistryDefinition,
            credentialDefinition = credentialDefinition,
        )
        // logger.info("processCredentialOptions: $processCredentialOptions")

        val credentialDefinitionJson: String =
            processCredentialOptions.credentialDefinition.toJson()
                ?: throw CredoError("credentialRequestMetadata not found")

        // logger.info("credentialDefinitionJson: $credentialDefinitionJson")

        val credentialDefinitionUniffi: CredentialDefinition =
            CredentialDefinition(credentialDefinitionJson)

        val jsonString = Gson().toJson(credentialRequestMetadata)
        val cleaned = jsonString.replace("\\\"", "")

        val credentialRequestMetadataUniffi: CredentialRequestMetadata =
            CredentialRequestMetadata(cleaned)

        var revocationRegistryDefinitionUniffi: RevocationRegistryDefinition? = null
        if (revocationRegistryDefinition != null) {
            val json = Gson().toJson(revocationRegistryDefinition)
            revocationRegistryDefinitionUniffi = RevocationRegistryDefinition(json)
        }

        val linkSecret = agent.anoncredsService.getLinkSecret(processCredentialOptions.linkSecret)

        val processedW3cCredential = W3cProcess().processCredential(
            cred = w3cCredential,
            credReqMetadata = credentialRequestMetadataUniffi,
            linkSecret = linkSecret,
            credDef = credentialDefinitionUniffi,
            revRegDef = revocationRegistryDefinitionUniffi,
        )

        return convertToW3cJsonLd(processedW3cCredential, processedW3cCredential.toJson())
    }

    private suspend fun storeW3cCredential(options: StoreCredentialW3cOptions): W3cCredentialRecord {
        val credential: W3cJsonLdVerifiableCredential = options.credential
        val credentialDefinitionId: String = options.credentialDefinitionId
        val schema: AnonCredsSchema = options.schema
        val credentialDefinition: AnonCredsCredentialDefinition = options.credentialDefinition
        val revocationRegistryId: String? = options.revocationRegistryId
        val credentialRequestMetadata: AnonCredsCredentialRequestMetadata = options.credentialRequestMetadata

        // logger.info("credential: $credential")

        val issuer = credential.issuer.toString()
        // logger.info("issuer: $issuer")

        val w3cJsonLdStr = Json.encodeToString(credential)
        LogUtil.info(this) {"w3cJsonLdStr: $w3cJsonLdStr"}

        var cleaned = w3cJsonLdStr.replace("\\\"", "")
        cleaned =
            Regex("\"credentialSubject\"\\s*:\\s*\\[(\\{.*?\\})\\]").replace(cleaned) { m ->
                val inner = m.groupValues[1]
                "\"credentialSubject\": $inner"
            }

        LogUtil.info(this) {"cleaned credential JSON: $cleaned"}

        val credentialUniffi: Credential =
            CredentialConversions().credentialFromW3cJson(cleaned)

        // logger.info("credentialUniffi: ${credentialUniffi.toJson()}")

        if (credential.credentialSubject.size > 1) {
            throw CredoError("Credential subject must be an object, not an array.")
        }

        val methodName = agent.anonCredsRegistryService
            .getRegistryForIdentifier(options.schemaId!!).methodName

        val tags = W3cAnonCredsUtils.getW3cRecordAnonCredsTags(
            credentialSubject = credential.credentialSubject.first(),
            issuerId = issuer,
            schemaId = credentialDefinition.schemaId,
            schema = schema,
            credentialDefinitionId = credentialDefinitionId,
            revocationRegistryId = revocationRegistryId,
            credentialRevocationId = credentialUniffi.revRegIndex()?.toString(),
            linkSecretId = credentialRequestMetadata.link_secret_name,
            methodName = methodName,
        )

        // logger.info("tags: $tags")

        val w3cCredentialRecord =
            agent.w3cCredentialService.storeCredentialW3cJsonLdVerifiableCredential(credential)

        val metadata = W3cAnonCredsCredentialMetadata(
            credentialRevocationId = tags["anonCredsCredentialRevocationId"],
            linkSecretId = tags["anonCredsLinkSecretId"]!!.trim('"'),
            methodName = tags["anonCredsMethodName"]!!,
        )

        w3cCredentialRecord.setTags(tags)

        val metadataJson = Json.encodeToJsonElement(metadata)
        w3cCredentialRecord.metadata.set(
            MetadataKeys.W3cAnonCredsCredentialMetadataKey,
            metadataJson,
        )

        agent.w3cCredentialRepository.update(w3cCredentialRecord)

        return w3cCredentialRecord
    }

    override suspend fun getCredential(
        credentialId: String,
        useUnqualifiedIdentifiersIfPresent: Boolean?,
    ): AnonCredsCredentialInfo {
        val w3cRecord = agent.w3cCredentialRepository.findById(credentialId)
        if (w3cRecord != null) {
            return getAnoncredsCredentialInfoFromRecord(
                w3cRecord,
                useUnqualifiedIdentifiersIfPresent,
            )
        }

        val legacyRecord =
            agent.anonCredsCredentialRepository.getByCredentialId(credentialId)

        return getAnoncredsCredentialInfoFromRecord(legacyRecord)
    }

    override suspend fun createCredentialRequest(options: CreateCredentialRequestOptions): CreateCredentialRequestReturn {
        val useLegacyProverDid = options.useLegacyProverDid
        val credentialDefinition = options.credentialDefinition
        val credentialOffer = options.credentialOffer
        val linkSecretId = options.linkSecretId

        val isLegacyIdentifier =
            Indyidentifiers.isUnqualifiedCredentialDefinitionId(credentialOffer.credDefId)

        if (!isLegacyIdentifier && useLegacyProverDid == true) {
            throw CredoError("Cannot use legacy prover_did with non-legacy identifiers")
        }

        val entropy =
            if ((useLegacyProverDid != null && !useLegacyProverDid) || !isLegacyIdentifier) {
                Verifier().generateNonce()
            } else {
                null
            }

        val proverDid =
            if (useLegacyProverDid == true) {
                Base58.encode(Verifier().generateNonce().substring(0, 16).toByteArray())
            } else {
                null
            }

        val linkSecret = agent.anoncredsService.getLinkSecret(linkSecretId!!)

        val createTuple: CredentialRequestTuple = Prover().createCredentialRequest(
            entropy = entropy,
            proverDid = proverDid,
            credDef = CredentialDefinition(credentialDefinition),
            linkSecret = linkSecret,
            linkSecretId = linkSecretId,
            credOffer = CredentialOffer(credentialOffer.toJsonString()),
        )

        val credentialRequest = createTuple.request
        val credentialRequestMetadata = createTuple.metadata

        val req = AnonCredsCredentialRequest.fromJsonString(credentialRequest.toJson())
        val metadata =
            AnonCredsCredentialRequestMetadata.fromJsonString(credentialRequestMetadata.toJson())

        return CreateCredentialRequestReturn(
            credentialRequest = req,
            credentialRequestMetadata = metadata,
        )
    }

    override suspend fun deleteCredential(credentialId: String) {
        val w3cRecord = agent.w3cCredentialRepository.findById(credentialId)
        if (w3cRecord != null) {
            agent.w3cCredentialRepository.delete(w3cRecord)
            return
        }

        val legacyRecord =
            agent.anonCredsCredentialRepository.getByCredentialId(credentialId)
        agent.anonCredsCredentialRepository.delete(legacyRecord)
    }

    override suspend fun createLinkSecret(options: CreateLinkSecretOptions?): CreateLinkSecretReturn {
        return CreateLinkSecretReturn(
            linkSecretId = options?.linkSecretId ?: BaseRecord.generateId(),
            linkSecret = anoncreds_uniffi.createLinkSecret(),
        )
    }

    private fun getAnoncredsCredentialInfoFromRecord(
        credentialRecord: Any,
        useUnqualifiedIdentifiersIfPresent: Boolean? = null,
    ): AnonCredsCredentialInfo {
        if (credentialRecord is W3cCredentialRecord) {
            // logger.info("W3cCredentialRecord detected")
            return W3cAnonCredsUtils.anonCredsCredentialInfoFromW3cRecord(
                credentialRecord,
                useUnqualifiedIdentifiersIfPresent,
            )
        } else {
            // logger.info("Legacy AnonCredsCredentialRecord detected")
            return W3cAnonCredsUtils.anonCredsCredentialInfoFromAnonCredsRecord(
                credentialRecord as AnonCredsCredentialRecord,
            )
        }
    }

    private suspend fun storeLinkSecret(options: StoreLinkSecretOptions): AnonCredsLinkSecretRecord {
        val (linkSecretId, linkSecretValue, setAsDefault) = options
        val record = AnonCredsLinkSecretRecord(
            linkSecretId = linkSecretId,
            value = linkSecretValue,
        )

        val currentDefault = agent.anonCredsLinkSecretRepository.findDefault()
        if (currentDefault == null || setAsDefault) {
            record.setTag("isDefault", true.toString())
        }

        if (currentDefault != null && setAsDefault) {
            currentDefault.setTag("isDefault", false.toString())
            agent.anonCredsLinkSecretRepository.update(currentDefault)
        }

        agent.anonCredsLinkSecretRepository.save(record)
        return record
    }

    fun generateNonce(): String {
        val random = SecureRandom()
        val bytes = ByteArray(10)
        random.nextBytes(bytes)
        val nonce = bytes.fold(BigInteger.ZERO) { acc, b ->
            (acc shl 8) or BigInteger.valueOf(b.toLong() and 0xFF)
        }
        return nonce.toString()
    }

    private fun toJsonObject(obj: Any): JsonObject {
        val json: JsonElement = Json.encodeToJsonElement(obj)
        return json.jsonObject
    }

    private fun queryFromRestrictions(
        restrictions: List<AnonCredsProofRequestRestriction>,
    ): MutableMap<String, String> {
        val queries = mutableListOf<Map<String, String>>()

        for (restriction in restrictions) {
            val q = mutableMapOf<String, String>()

            restriction.credDefId?.let {
                if (Indyidentifiers.isUnqualifiedCredentialDefinitionId(it)) {
                    q["anonCredsUnqualifiedCredentialDefinitionId"] = it
                } else {
                    q["anonCredsCredentialDefinitionId"] = it
                }
            }

            run {
                val issuerId = restriction.issuerId ?: restriction.issuerDid
                if (issuerId != null) {
                    if (Indyidentifiers.isUnqualifiedIndyDid(issuerId)) {
                        q["anonCredsUnqualifiedIssuerId"] = issuerId
                    } else {
                        q["issuerId"] = issuerId
                    }
                }
            }

            restriction.schemaId?.let {
                if (Indyidentifiers.isUnqualifiedSchemaId(it)) {
                    q["anonCredsUnqualifiedSchemaId"] = it
                } else {
                    q["anonCredsSchemaId"] = it
                }
            }

            run {
                val schemaIssuerId = restriction.schemaIssuerId ?: restriction.schemaIssuerDid
                if (schemaIssuerId != null) {
                    if (Indyidentifiers.isUnqualifiedIndyDid(schemaIssuerId)) {
                        q["anonCredsUnqualifiedSchemaIssuerId"] = schemaIssuerId
                    } else {
                        q["anonCredsSchemaIssuerId"] = schemaIssuerId
                    }
                }
            }

            restriction.schemaName?.let { q["anonCredsSchemaName"] = it }
            restriction.schemaVersion?.let { q["anonCredsSchemaVersion"] = it }

            for ((name, value) in restriction.attributeValues) {
                q["anonCredsAttr::$name::value"] = value
            }

            for ((name, active) in restriction.attributeMarkers) {
                if (active) {
                    q["anonCredsAttr::$name::marker"] = true.toString()
                }
            }

            queries += q
        }

        return queries.first().toMutableMap()
    }

    private suspend fun getLegacyCredentialsForProofRequest(
        options: GetCredentialsForProofRequestOptions,
    ): GetCredentialsForProofRequestReturn {
        val proofRequest = options.proofRequest
        val referent = options.attributeReferent

        val requested = proofRequest.requestedAttributes[referent]
            ?: proofRequest.requestedPredicates[referent]
            ?: throw AnonCredsRsError("Referent not found in proof request")

        val andClauses = mutableListOf<Any>()

        val attributes = when (requested) {
            is AnonCredsRequestedAttribute ->
                requested.names ?: listOfNotNull(requested.name)

            is AnonCredsRequestedPredicate ->
                listOfNotNull(requested.name)

            else -> error("Unexpected requested attribute type")
        }

        val attrQuery = mutableMapOf<String, Any?>()
        attributes.forEach { attr ->
            attrQuery["anonCredsAttr::$attr::marker"] = true
        }
        andClauses += attrQuery

        when (requested) {
            is AnonCredsRequestedAttribute -> {
                val rest = requested.restrictions
                if (!rest.isNullOrEmpty()) {
                    andClauses += queryLegacyFromRestrictions(rest)
                }
            }

            is AnonCredsRequestedPredicate -> {
                val rest = requested.restrictions
                if (!rest.isNullOrEmpty()) {
                    andClauses += queryLegacyFromRestrictions(rest)
                }
            }
        }

        options.extraQuery?.let { andClauses += it }

        val finalQuery = mapOf("\$and" to andClauses)

        val credentials =
            agent.anonCredsCredentialRepository.findByQuery(finalQuery.toString())

        val list = credentials.map { rec ->
            CredentialForProofRequest(
                credentialInfo = getAnoncredsCredentialInfoFromRecord(rec),
                interval = proofRequest.nonRevoked,
            )
        }

        return GetCredentialsForProofRequestReturn(list)
    }

    private fun queryLegacyFromRestrictions(
        restrictions: List<AnonCredsProofRequestRestriction>,
    ): Map<String, Any?> {
        val queries = mutableListOf<Map<String, Any?>>()

        for (r in restrictions) {
            val q = mutableMapOf<String, Any?>()
            val extra = mutableMapOf<String, Any?>()

            r.credDefId?.let {
                q["credentialDefinitionId"] = it
                if (Indyidentifiers.isUnqualifiedCredentialDefinitionId(it)) {
                    extra["credentialDefinitionId"] = it
                }
            }

            run {
                val issuerId = r.issuerId ?: r.issuerDid
                if (issuerId != null) {
                    q["issuerId"] = issuerId
                    if (Indyidentifiers.isUnqualifiedIndyDid(issuerId)) {
                        extra["issuerId"] = issuerId
                    }
                }
            }

            r.schemaId?.let {
                q["schemaId"] = it
                if (Indyidentifiers.isUnqualifiedSchemaId(it)) {
                    extra["schemaId"] = it
                }
            }

            run {
                val schemaIssuerId = r.schemaIssuerId ?: r.schemaIssuerDid
                if (schemaIssuerId != null) {
                    q["schemaIssuerId"] = schemaIssuerId
                    if (Indyidentifiers.isUnqualifiedIndyDid(schemaIssuerId)) {
                        extra["schemaIssuerId"] = schemaIssuerId
                    }
                }
            }

            r.schemaName?.let { q["schemaName"] = it }
            r.schemaVersion?.let { q["schemaVersion"] = it }

            for ((name, value) in r.attributeValues) {
                q["attr::$name::value"] = value
            }

            for ((name, active) in r.attributeMarkers) {
                if (active) {
                    q["attr::$name::marker"] = true
                }
            }

            queries += q
            if (extra.isNotEmpty()) queries += extra
        }

        return if (queries.size == 1) {
            queries.first()
        } else {
            mapOf("\$or" to queries)
        }
    }
}
