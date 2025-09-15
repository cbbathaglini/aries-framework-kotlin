package org.hyperledger.ariesframework.anoncreds.service

import anoncreds_uniffi.Credential
import anoncreds_uniffi.CredentialConversions
import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.CredentialOffer
import anoncreds_uniffi.CredentialRequestMetadata
import anoncreds_uniffi.CredentialRequestTuple
import anoncreds_uniffi.CredentialRevocationState
import anoncreds_uniffi.Issuer
import anoncreds_uniffi.Presentation
import anoncreds_uniffi.PresentationRequest
import anoncreds_uniffi.Prover
import anoncreds_uniffi.RequestedCredential
import anoncreds_uniffi.RevocationRegistryDefinition
import anoncreds_uniffi.RevocationRegistryDefinitionTuple
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
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryEntry
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.anoncreds.model.CreateCredentialRequestOptions
import org.hyperledger.ariesframework.anoncreds.model.LegacyToW3cCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.ProcessOptions
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
import org.hyperledger.ariesframework.proofs.v2.ProofUtils
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.toJsonString
import org.hyperledger.ariesframework.util.Base58
import org.hyperledger.ariesframework.util.JsonUtils
import org.hyperledger.ariesframework.util.PrintLongLine
import org.hyperledger.ariesframework.util.concurrentForEach
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

        logger.info("credential: $credential")
        logger.info("credentialDefinition: $credentialDefinition")
        logger.info("credentialRequestMetadata: $credentialRequestMetadata")
        logger.info("revocationRegistry: $revocationRegistry")

        var w3cJsonLdCredential: W3cJsonLdVerifiableCredential
        if (credential is W3cJsonLdVerifiableCredential) {
            w3cJsonLdCredential = credential
            logger.info("w3cJsonLdCredential: $w3cJsonLdCredential")
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
            logger.info("legacyToW3cCredentialOptions: $legacyToW3cCredentialOptions")

            w3cJsonLdCredential = legacyToW3cCredential(legacyToW3cCredentialOptions)
            logger.info("w3cJsonLdCredential: $w3cJsonLdCredential")
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
        logger.info("w3cCredentialRecord: $w3cCredentialRecord")

        return w3cCredentialRecord.id
    }

    override suspend fun legacyToW3cCredential(options: LegacyToW3cCredentialOptions): W3cJsonLdVerifiableCredential {
        val anonCredsCredential: AnonCredsCredential = options.credential
        val issuerId: String = options.issuerId
        val processOptions: ProcessOptions? = options.processOptions

        logger.info("anonCredsCredential: $anonCredsCredential")

        // val anonCredsCredentialJson = Gson().toJson(anonCredsCredential)

        val anonCredsCredentialJson = Json.encodeToString(anonCredsCredential)
        PrintLongLine.print(anonCredsCredentialJson)

        var credential: Credential = Credential(anonCredsCredentialJson)
        logger.info("credential: $credential")

        val credentialW3cStr: String =
            CredentialConversions().credentialToW3cJson(credential, issuerId, "1.1")
        logger.info("credentialW3cStr: $credentialW3cStr")

        val w3cCredential: W3cCredential = W3cCredential(credentialW3cStr)

        val w3cJsonLdVerifiableCredential: W3cJsonLdVerifiableCredential =
            convertToW3cJsonLd(w3cCredential, credentialW3cStr)

        var w3cJsonLdVC = w3cJsonLdVerifiableCredential
        if (processOptions != null) {
            logger.info("processOptions: $processOptions")
            w3cJsonLdVC =
                processW3cCredential(w3cCredential, w3cJsonLdVerifiableCredential, processOptions)
        }

        // logger.info("aaaaaaaaa======== ${agent.w3cCredentialRepository.getAll().toString()}")

//        } finally {
//            anonCredsCredential?.handle?.clear()
//            w3cCredentialObj?.handle?.clear()
//        }

        return w3cJsonLdVC
    }

    // todo
    override suspend fun createProof(
        options: CreateProofOptions,
    ): AnonCredsProof { // AnonCredsProof {
        val requestMessage = options.requestMessage
        val proofFormats = options.proofFormats
        val credentialDefinitions = options.credentialDefinitions
        val proofRequest = options.proofRequest
        val selectedCredentials = options.selectedCredentials
        val schemas = options.schemas

        var presentation: Presentation? = null

        // Record<string, JsonObject> vira Map<String, JsonObject>
        val rsCredentialDefinitions: MutableMap<String, JsonObject> = mutableMapOf()
        for ((credDefId, credDef) in credentialDefinitions.credentialDefinitions) {
            val credDefJsonObject: JsonObject = Json.parseToJsonElement(credDef.toJson()).jsonObject
            rsCredentialDefinitions[credDefId] = credDefJsonObject
        }

        val rsSchemas: MutableMap<String, JsonObject> = mutableMapOf()
        for ((schemaId, schema) in schemas.schemas) {
            val schemaJsonObject: JsonObject = Json.parseToJsonElement(schema.toJson()).jsonObject
            rsSchemas[schemaId] = schemaJsonObject
        }

        // Cache para minimizar chamadas de storage
        val retrievedCredentials: MutableMap<String, Any> = mutableMapOf()

        fun getCredentialUniffiByW3cCredentialRecord(credentialRecord: W3cCredentialRecord): Credential {
            val cred = credentialRecord.credential.toJson()

            val jsonld: W3cJsonLdVerifiableCredential = W3cJsonLdVerifiableCredential.fromJson(cred)
            val w3cJsonLdVerifiableCredentialStr = Json.encodeToString(jsonld)
            var w3cJsonLdVerifiableCredentialStrClean =
                w3cJsonLdVerifiableCredentialStr.replace("\\\"", "")
            w3cJsonLdVerifiableCredentialStrClean =
                Regex("\"credentialSubject\"\\s*:\\s*\\[(\\{.*?\\})\\]")
                    .replace(w3cJsonLdVerifiableCredentialStrClean) { matchResult ->
                        val inner = matchResult.groupValues[1]
                        "\"credentialSubject\": $inner"
                    }

            logger.info("w3cJsonLdVerifiableCredentialStr PROOF: $w3cJsonLdVerifiableCredentialStrClean ")
            return CredentialConversions().credentialFromW3cJson(
                w3cJsonLdVerifiableCredentialStrClean,
            )
        }

        suspend fun credentialEntryFromAttribute(
            attribute: Any,
        ): CredentialEntryResult {
            val credentialId = when (attribute) {
                is AnonCredsRequestedAttributeMatch -> attribute.credentialId
                is AnonCredsRequestedPredicateMatch -> attribute.credentialId
                else -> error("Tipo inesperado ${attribute::class}")
            }

            var credentialRecord = retrievedCredentials[credentialId]

            if (credentialRecord == null) {
                val w3cCredentialRecord =
                    agent.w3cCredentialRepository.findById(credentialId)
                credentialRecord = if (w3cCredentialRecord != null) {
                    retrievedCredentials.put(credentialId, w3cCredentialRecord)
                    w3cCredentialRecord
                } else {
                    val legacy =
                        agent.anonCredsCredentialRepository.getByCredentialId(credentialId)
                    logger.warn(
                        """
                        Creating AnonCreds proof with legacy credential $credentialId.
                        Please run the migration script to migrate credentials to the new W3C format.
                        See https://credo.js.org/guides/updating/versions/0.4-to-0.5 for information on how to migrate.
                        """.trimIndent(),
                    )
                    retrievedCredentials.put(credentialId, legacy)
                    legacy
                }
            }

            val proofUsesUnqualifiedIdentifiers =
                ProofRequestOperations.proofRequestUsesUnqualifiedIdentifiers(proofRequest)

            val info: AnonCredsCredentialInfo = getAnoncredsCredentialInfoFromRecord(
                credentialRecord,
                proofUsesUnqualifiedIdentifiers,
            )

            val timestamp = when (attribute) {
                is AnonCredsRequestedAttributeMatch -> attribute.timestamp
                is AnonCredsRequestedPredicateMatch -> attribute.timestamp
                else -> null
            }

            var revocationState: CredentialRevocationState? = null
            var revocationRegistryDefinition: RevocationRegistryDefinition? = null

            if (timestamp != null && info.credentialRevocationId != null && info.revocationRegistryId != null) {
                val registryData: AnonCredsRevocationRegistryEntry =
                    options.revocationRegistries.get(info.revocationRegistryId)
                        ?: throw AnonCredsRsError("Revocation Registry ${info.revocationRegistryId} not found")

                val revocationStatusList = registryData.revocationStatusLists?.get(timestamp)
                    ?: throw CredoError(
                        "Revocation status list for revocation registry ${info.revocationRegistryId} and timestamp $timestamp not found",
                    )

                val registryDataDefinition = registryData.definition
//                val registryValue = registryDataDefinition.value
//                val registryValueJson: JsonElement =
//                    Json.encodeToJsonElement(RevocationRegistryValue.serializer(), registryValue)
//                val registryValueString: String =
//                    Json { prettyPrint = true }.encodeToString(registryValueJson)

                /*RevocationRegistryDefinition -->
                 fun `issuerId`(): String
                fun `maxCredNum`(): UInt
                fun `revRegId`(): String
                fun `tailsHash`(): String
                fun `tailsLocation`(): String
                fun `toJson`(): String


                indy besu

                 var `issuerId`: String,
                var `revocDefType`: String,
                var `credDefId`: String,
                var `tag`: String,
                var `value`: JsonValue
                 */

//                val revocationRegistryDefinitionIndyBesu = uniffi.indy_besu_vdr.RevocationRegistryDefinition(
//                    revocDefType = registryDataDefinition.revocDefType,
//                    credDefId = registryDataDefinition.credDefId,
//                    tag = registryDataDefinition.tag,
//                    value = registryValueString,
//                    issuerId = registryDataDefinition.issuerId
//                )
//
//                //val revocationStatusList = RevocationStatusList()
//
//                val revocationStatusListIndyBesu = uniffi.indy_besu_vdr.RevocationStatusList(
//                    issuerId = revocationStatusList.issuerId,
//                    revRegDefId = revocationStatusList.revRegDefId,
//                    timestamp = revocationStatusList.timestamp.toULong(),
//                    revocationList = revocationStatusList.revocationList.map { it.toUInt() },
//                    currentAccumulator = revocationStatusList.currentAccumulator
//                )

                val tails = agent.ledgerService.getTailsPath()
                val credentialDefinitionStr =
                    agent.ledgerService.getCredentialDefinition(registryDataDefinition.credDefId)
                val credentialDefinition = credentialDefinitionStr.replace("\\\"", "\"")

                var credentialDefinitionUniffi: CredentialDefinition =
                    CredentialDefinition(credentialDefinition)

                val revocationRegistryDefinitionAnoncreds: RevocationRegistryDefinitionTuple =
                    Issuer().createRevocationRegistryDef(
                        credDef = credentialDefinitionUniffi,
                        credDefId = registryDataDefinition.credDefId,
                        tag = registryDataDefinition.tag,
                        maxCredNum = 1000U,
                        tailsDirPath = registryData.tailsFilePath,
                    )

                var revocationStatusListAnoncreds: RevocationStatusList? = null

                try {
                    revocationStatusListAnoncreds = Issuer().createRevocationStatusList(
                        revRegDefId = revocationStatusList.revRegDefId,
                        timestamp = revocationStatusList.timestamp.toULong(),
                        credDef = credentialDefinitionUniffi,
                        revRegDef = revocationRegistryDefinitionAnoncreds.revRegDef,
                        revRegPriv = revocationRegistryDefinitionAnoncreds.revRegDefPriv,
                        issuanceByDefault = true,
                    )
                    logger.info("revocationStatusListAnoncreds: ${revocationStatusListAnoncreds.toJson()}")
                } catch (e: Exception) {
                    logger.error("revocationStatusListAnoncreds error: ${e.message}")
                }

                val tailsFile = File(
                    registryData.tailsFilePath,
                    "${registryData.tailsHash}",
                ) // ou + ".tails" se for esse o padrão
                require(tailsFile.exists()) { "Tails file not found at ${tailsFile.absolutePath}" }
                logger.info("tails file: ${tailsFile.absolutePath}")

                try {
                    revocationState = Prover().createOrUpdateRevocationState(
                        revRegDef = revocationRegistryDefinitionAnoncreds.revRegDef,
                        revStatusList = revocationStatusListAnoncreds!!,
                        revRegIdx = info.credentialRevocationId.toUInt(),
                        tailsPath = tailsFile.absolutePath,
                        revState = null,
                        oldRevStatusList = null,
                    )
                } catch (e: Exception) {
                    logger.error("error prover: ${e.message}")
                }
            }

            // can be Credential or AnoncredsCredential
            val credential: Any = if (credentialRecord is W3cCredentialRecord) {
                getCredentialUniffiByW3cCredentialRecord(credentialRecord)
            } else {
                (credentialRecord as AnonCredsCredentialRecord).credencial
            }

            // todo
            var newCredential: Any
            if (proofUsesUnqualifiedIdentifiers) {
                if (credential is Credential) {
                    val credUniffi = credential.toJson()
//                    credential.schema_id = info.schemaId
//                    credential.cred_def_id = info.credentialDefinitionId
//                    credential.rev_reg_id = info.revocationRegistryId
                } else if (credential is AnonCredsCredential) {
                }
            }

            var revocationStateJsonElement: JsonElement? = null
            if (revocationState != null) {
                revocationStateJsonElement =
                    Json.parseToJsonElement(revocationState.toJson())
            }

            var credJsonElement: JsonElement = if (credential is Credential) {
                Json.parseToJsonElement(credential.toJson())
            } else {
                credential as AnonCredsCredential
                Json.parseToJsonElement(credential.toJson())
            }

            return CredentialEntryResult(
                linkSecretId = info.linkSecretId,
                credentialId = credentialId,
                credentialEntry = CredentialEntry(
                    credential = credJsonElement,
                    revocationState = revocationStateJsonElement,
                    timestamp = timestamp,
                ),
            )
        }

        val credentialsProve = mutableListOf<CredentialProve>()
        val credentials = mutableListOf<CredentialEntryResult>()
        var entryIndex = 0

        for ((referent, attribute) in selectedCredentials.attributes) {
            val existingIndex = credentials.indexOfFirst {
                it.credentialId == attribute.credentialId &&
                    it.credentialEntry.timestamp == attribute.timestamp
            }

            if (existingIndex != -1) {
                credentialsProve.add(
                    CredentialProve(
                        entryIndex = existingIndex,
                        referent = referent,
                        isPredicate = false,
                        reveal = attribute.revealed,
                    ),
                )
            } else {
                credentials.add(credentialEntryFromAttribute(attribute))
                credentialsProve.add(
                    CredentialProve(
                        entryIndex = existingIndex,
                        referent = referent,
                        isPredicate = false,
                        reveal = attribute.revealed,
                    ),
                )
                entryIndex++
            }
        }

        for ((referent, predicate) in selectedCredentials.predicates) {
            val existingIndex = credentials.indexOfFirst {
                it.credentialId == predicate.credentialId &&
                    it.credentialEntry.timestamp == predicate.timestamp
            }

            if (existingIndex != -1) {
                credentialsProve.add(
                    CredentialProve(
                        entryIndex = existingIndex,
                        referent = referent,
                        isPredicate = true,
                        reveal = true,
                    ),
                )
            } else {
                credentials.add(credentialEntryFromAttribute(predicate))
                credentialsProve.add(
                    CredentialProve(
                        entryIndex = existingIndex,
                        referent = referent,
                        isPredicate = true,
                        reveal = true,
                    ),
                )
                entryIndex++
            }
        }

        // val credentialsMapsJsons : List<String> = credentials.map { it.credentialEntry.toJson() }

        val credentialsMaps: List<JsonElement> = credentials.map {
            Json.parseToJsonElement(it.credentialEntry.toJson())
        }

        val linkSecretIds = credentials.map { it.linkSecretId }
        val linkSecretId = assertLinkSecretsMatch(linkSecretIds)
        val linkSecret = agent.anoncredsService.getLinkSecret(linkSecretId)

        val map: Map<String, Any?> = mapOf(
            "credentialDefinitions" to rsCredentialDefinitions,
            "schemas" to rsSchemas,
            "presentationRequest" to Json.parseToJsonElement(proofRequest.toJson()),
            "credentials" to credentialsMaps,
            "credentialsProve" to credentialsProve,
            "selfAttest" to selectedCredentials.selfAttestedAttributes,
            "linkSecret" to linkSecret,
        )

        val jsonString = JsonUtils.mapToJson2(map).toString()

        val anoncredsCreds = mutableListOf<RequestedCredential>()
        val schemaIds = mutableSetOf<String>()
        val credentialDefinitionIds = mutableSetOf<String>()

        val requestedCredentials: RequestedCredentialsAnoncreds =
            RequestedCredentialsAnoncreds.mapToRequestedCredentialsWithKotlinx(proofFormats!!)
        val credentialIds = requestedCredentials.getCredentialIdentifiers()

        credentialIds.concurrentForEach { credId ->
            logger.info("id: $credId")
            val w3cs = agent.w3cCredentialRepository.getAll()
            w3cs.forEach { cred ->
                logger.error("w3c --> $cred")
            }

            val credex = agent.credentialExchangeRepository.getAll()
            credex.forEach { cred ->
                logger.error("credex --> $cred")
            }

            val credentialRecord = agent.w3cCredentialRepository.getById(credId)
            val credential = getCredentialUniffiByW3cCredentialRecord(credentialRecord)
            schemaIds.add(credential.schemaId())
            credentialDefinitionIds.add(credential.credDefId())

            val requestedAttributes = mutableMapOf<String, Boolean>()
            val requestedPredicates = mutableListOf<String>()
            var timestamp: Int? = null
            requestedCredentials.requestedAttributes.forEach { (referent, attr) ->
                if (attr.credentialId == credId) {
                    requestedAttributes[referent] = attr.revealed
                    if (attr.timestamp != null) {
                        timestamp = max(attr.timestamp, timestamp ?: 0)
                    }
                }
            }
            requestedCredentials.requestedPredicates.forEach { (referent, pred) ->
                if (pred.credentialId == credId) {
                    requestedPredicates.add(referent)
                    if (pred.timestamp != null) {
                        timestamp = max(pred.timestamp, timestamp ?: 0)
                    }
                }
            }

            val revocationState = if (timestamp != null) {
                agent.revocationService.createRevocationState(credential, timestamp!!)
            } else {
                null
            }

            val requestedCredential = RequestedCredential(
                credential,
                timestamp?.toULong(),
                revocationState,
                requestedAttributes,
                requestedPredicates,
            )
            anoncredsCreds.add(requestedCredential)
        }

        val schemasMap: Map<String, Schema> = ProofUtils.getSchemasUniffi(agent, schemaIds)
        val credentialDefinitionsMap: Map<String, CredentialDefinition> =
            ProofUtils.getCredentialDefinitionsUniffi(agent, credentialDefinitionIds)

        val createPresentation = Prover().createPresentation(
            PresentationRequest(requestMessage.anoncredsProofRequest()),
            anoncredsCreds,
            emptyMap(),
            linkSecret,
            schemasMap,
            credentialDefinitionsMap,
        )

        val anonCredsProof: AnonCredsProof =
            Json.decodeFromString<AnonCredsProof>(createPresentation.toJson())

        return anonCredsProof
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

        val credentials = agent.w3cCredentialRepository.findByQuery(tags.toJsonString())

        val filteredCredentials = credentials.filter { rec ->
            attibutesList.all(rec.getTags()::containsKey)
        }

        val legacyCredentialWithMetadata: List<CredentialForProofRequest> =
            getLegacyCredentialsForProofRequest(options).credentials

        if (legacyCredentialWithMetadata.isNotEmpty()) {
            logger.warn(
                listOf(
                    "Including legacy credentials in proof request.",
                    "Please run the migration script to migrate credentials to the new W3C format.",
                ).joinToString("\n"),
            )
        }

        // mapear os atuais para { credentialInfo, interval }
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
        logger.info("w3cCredential: ${w3cCredential.toJson()}")

        val element = Json.parseToJsonElement(w3cCredential.toJson()).jsonObject.toMutableMap()
        logger.info("element: $element")

        val subject = element["credentialSubject"]
        if (subject != null && subject !is JsonArray) {
            element["credentialSubject"] = JsonArray(listOf(subject))
        }

        val typeCred = element["type"]
        if (typeCred != null && typeCred !is JsonArray) {
            element["type"] = JsonArray(listOf(typeCred))
        }

        val normalized = JsonObject(element)
        logger.info("normalized: $normalized")

        val w3cJsonLdVerifiableCredential: W3cJsonLdVerifiableCredential =
            W3cJsonLdVerifiableCredential.fromJson(normalized.toString())
        // val  w3cJsonLdVerifiableCredential : W3cJsonLdVerifiableCredential = Gson().fromJson(normalized, W3cJsonLdVerifiableCredential::class.java)
        logger.info("w3cJsonLdVerifiableCredential: $w3cJsonLdVerifiableCredential")

        return w3cJsonLdVerifiableCredential
    }

    private suspend fun processW3cCredential(
        w3cCredential: W3cCredential,
        crew3cJsonLdVC: W3cJsonLdVerifiableCredential,
        processOptions: ProcessOptions,
    ): W3cJsonLdVerifiableCredential {
        val mapper: ObjectMapper = jacksonObjectMapper()
        val (credentialDefinition, credentialRequestMetadata, revocationRegistryDefinition) = processOptions

        val processCredentialOptions = ProcessCredentialOptions(
            credentialRequestMetadata = credentialRequestMetadata,
            linkSecret = agent.wallet.linkSecretId!!,
            revocationRegistryDefinition = revocationRegistryDefinition,
            credentialDefinition = credentialDefinition,
        )
        logger.info("credentialRequestMetadata: $credentialRequestMetadata ")
        logger.info("processCredentialOptions: $processCredentialOptions ")
        logger.info("revocationRegistryDefinition: ${processCredentialOptions.revocationRegistryDefinition} ")
        val credentialDefinitionJson: String =
            processCredentialOptions.credentialDefinition.toJson()
                ?: throw CredoError("credentialRequestMetadata not found")
        logger.info("credentialDefinitionJson: $credentialDefinitionJson ")

        val credentialDefinitionUniffi: CredentialDefinition =
            CredentialDefinition(credentialDefinitionJson)
        logger.info("credentialDefinitionUniffi: ${credentialDefinitionUniffi.toJson()} ")

        val jsonString = Gson().toJson(credentialRequestMetadata)
        val cleaned = jsonString.replace("\\\"", "")
        logger.info("jsonString: $cleaned")

        val credentialRequestMetadataUniffi: CredentialRequestMetadata =
            CredentialRequestMetadata(cleaned)
        logger.info("credentialRequestMetadataUniffi: ${credentialRequestMetadataUniffi.toJson()} ")

        var revocationRegistryDefinitionUniffi: RevocationRegistryDefinition? = null
        if (revocationRegistryDefinition != null) {
            val anonCredsRevocationRegistryDefinitionJson =
                Gson().toJson(revocationRegistryDefinition)
                    ?: throw CredoError("revocationRegistryDefinition not found")
            logger.info("anonCredsRevocationRegistryDefinitionJson: $anonCredsRevocationRegistryDefinitionJson")
            revocationRegistryDefinitionUniffi =
                RevocationRegistryDefinition(anonCredsRevocationRegistryDefinitionJson)
            logger.info("revocationRegistryDefinitionUniffi: $revocationRegistryDefinitionUniffi ")
        }
        logger.info("w3cCredential: ${w3cCredential.toJson()} ")
        logger.info("credReqMetadata: ${credentialRequestMetadataUniffi.toJson()} ")
        logger.info("linkSecret: ${processCredentialOptions.linkSecret} ")
        logger.info("credDef: ${credentialDefinitionUniffi.toJson()} ")
        logger.info("revRegDef: $revocationRegistryDefinitionUniffi ")

        val linkSecret = agent.anoncredsService.getLinkSecret(processCredentialOptions.linkSecret)
        logger.info("linkSecret: $linkSecret ")

        val processedW3cCredential = W3cProcess().processCredential(
            cred = w3cCredential,
            credReqMetadata = credentialRequestMetadataUniffi,
            linkSecret = linkSecret,
            credDef = credentialDefinitionUniffi,
            revRegDef = revocationRegistryDefinitionUniffi,
        )
        logger.info("processedW3cCredential: ${processedW3cCredential.toJson()} ")

        return convertToW3cJsonLd(processedW3cCredential, processedW3cCredential.toJson())
    }

    private suspend fun storeW3cCredential(options: StoreCredentialW3cOptions): W3cCredentialRecord {
        val credential: W3cJsonLdVerifiableCredential = options.credential
        val credentialDefinitionId: String = options.credentialDefinitionId
        val schema: AnonCredsSchema = options.schema
        val credentialDefinition: AnonCredsCredentialDefinition = options.credentialDefinition
        // val revocationRegistryDefinition: AnonCredsRevocationRegistryDefinition? = options.revocationRegistryDefinition
        val revocationRegistryId: String? = options.revocationRegistryId
        val credentialRequestMetadata: AnonCredsCredentialRequestMetadata =
            options.credentialRequestMetadata

        logger.info("credential: $credential ")
        val issuer = credential.issuer.toString()
        logger.info("issuer: $issuer ")

        val w3cJsonLdVerifiableCredentialStr = Json.encodeToString(credential)
        var w3cJsonLdVerifiableCredentialStrClean =
            w3cJsonLdVerifiableCredentialStr.replace("\\\"", "")
        w3cJsonLdVerifiableCredentialStrClean =
            Regex("\"credentialSubject\"\\s*:\\s*\\[(\\{.*?\\})\\]")
                .replace(w3cJsonLdVerifiableCredentialStrClean) { matchResult ->
                    val inner = matchResult.groupValues[1]
                    "\"credentialSubject\": $inner"
                }

        logger.info("w3cJsonLdVerifiableCredentialStr: $w3cJsonLdVerifiableCredentialStrClean ")

        val credentialUniffi: Credential =
            CredentialConversions().credentialFromW3cJson(w3cJsonLdVerifiableCredentialStrClean)

        logger.info("credentialUniffi: ${credentialUniffi.toJson()} ")

        val credentialW3cStr: String = CredentialConversions().credentialToW3cJson(
            credentialUniffi,
            "did:sov:" + issuer,
            "1.1",
        )
        logger.info("credentialW3cStr: $credentialW3cStr ")

        val w3cCredential: W3cCredential = W3cCredential(credentialW3cStr)
        logger.info("credentialUniffi: ${credentialUniffi.toJson()} ")

//        //var anonCredsCredential: Credential = CredentialConversions().credentialFromW3cJson(credentialJson)
//        val w3cAnonCredsCredential = W3cCredential.fromJson(credentialJson) //[TODO] uniffi another version with w3c

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
            credentialRevocationId = credentialUniffi.revRegIndex()
                ?.toString(), // w3ccredential revRegIndex
            linkSecretId = credentialRequestMetadata.link_secret_name,
            methodName = methodName,
        )
        logger.info("tags: $tags ")

        val w3cCredentialRecord =
            agent.w3cCredentialService.storeCredentialW3cJsonLdVerifiableCredential(credential)
        logger.info("w3cCredentialRecord => $w3cCredentialRecord ")

        val anonCredsCredentialMetadata: W3cAnonCredsCredentialMetadata =
            W3cAnonCredsCredentialMetadata(
                credentialRevocationId = tags["anonCredsCredentialRevocationId"],
                linkSecretId = tags["anonCredsLinkSecretId"]!!.trim('"'),
                methodName = tags["anonCredsMethodName"]!!,
            )

        w3cCredentialRecord.setTags(tags)

        val anonCredsCredentialMetadataJson = Json.encodeToJsonElement(anonCredsCredentialMetadata)
        logger.info("anonCredsCredentialMetadataJson ========> $anonCredsCredentialMetadataJson ")
        w3cCredentialRecord.metadata.set(
            MetadataKeys.W3cAnonCredsCredentialMetadataKey,
            anonCredsCredentialMetadataJson,
        )

        logger.info("w3cCredentialRecord ========> $w3cCredentialRecord ")
        agent.w3cCredentialRepository.update(w3cCredentialRecord)

        return w3cCredentialRecord
    }

    override suspend fun getCredential(
        credentialId: String,
        useUnqualifiedIdentifiersIfPresent: Boolean?,
    ): AnonCredsCredentialInfo {
        val w3cCredentialRecord = agent.w3cCredentialRepository.findById(credentialId)
        logger.info("w3cCredentialRecord <> $w3cCredentialRecord")
        if (w3cCredentialRecord != null) {
            return getAnoncredsCredentialInfoFromRecord(
                w3cCredentialRecord,
                useUnqualifiedIdentifiersIfPresent,
            )
        }

        val anonCredsCredentialRecord =
            agent.anonCredsCredentialRepository.getByCredentialId(credentialId)

        logger.warn("Querying legacy credential repository for credential with id $credentialId. Please run the migration script to migrate credentials to the new w3c format.")
        logger.info("Querying legacy credential repository for credential with id $credentialId. Please run the migration script to migrate credentials to the new w3c format.")
        return getAnoncredsCredentialInfoFromRecord(
            anonCredsCredentialRecord,
        )
    }

    override suspend fun createCredentialRequest(options: CreateCredentialRequestOptions): CreateCredentialRequestReturn {
        val useLegacyProverDid = options.useLegacyProverDid
        val credentialDefinition = options.credentialDefinition
        val credentialOffer = options.credentialOffer
        val linkSecretId = options.linkSecretId

        /*var linkSecretRecord : AnonCredsLinkSecretRecord? = agent.anonCredsLinkSecretRepository.findDefault()
        if (linkSecretId != null) {
            linkSecretRecord =
                agent.anonCredsLinkSecretRepository.getByLinkSecretId(linkSecretId)
        }

        if (linkSecretRecord == null) {
            if (agent.anoncredsmodulesconfig.autoCreateLinkSecret != null) {
                throw AnonCredsRsError("No link secret provided to createCredentialRequest and no default link secret has been found")
            }

            val (linkSecretId, linkSecretValue) = createLinkSecret()
            val options = StoreLinkSecretOptions(
                linkSecretId = linkSecretId,
                linkSecretValue = linkSecretValue,
                setAsDefault = true
            )
            linkSecretRecord = storeLinkSecret(options)
        }

        if (linkSecretRecord.value == null) {
            throw AnonCredsRsError("Link Secret value not stored")
        }*/

        val isLegacyIdentifier =
            Indyidentifiers.isUnqualifiedCredentialDefinitionId(credentialOffer.credDefId)

        if (!isLegacyIdentifier && useLegacyProverDid == true) {
            throw CredoError("Cannot use legacy prover_did with non-legacy identifiers")
        }

        val entropy =
            if ((useLegacyProverDid != null && !useLegacyProverDid) || !isLegacyIdentifier) Verifier().generateNonce() else null // [TODO] anoncreds came from uniffi
        val proverDid = if (useLegacyProverDid != null && useLegacyProverDid == true) {
            Base58.encode(Verifier().generateNonce().substring(0, 16).toByteArray())
        } else {
            null
        }

        val linkSecret = agent.anoncredsService.getLinkSecret(linkSecretId!!)
        val createReturnObj: CredentialRequestTuple = Prover().createCredentialRequest(
            entropy = entropy,
            proverDid = proverDid,
            credDef = CredentialDefinition(credentialDefinition),
            linkSecret = linkSecret,
            linkSecretId = linkSecretId,
            credOffer = CredentialOffer(credentialOffer.toJsonString()),
        )

        val credentialRequest = createReturnObj.request // CredentialRequest
        val credentialRequestMetadata = createReturnObj.metadata // CredentialRequestMetadata

        val anonCredsCredentialRequest =
            AnonCredsCredentialRequest.fromJsonString(credentialRequest.toJson())
        val anonCredsCredentialRequestMetadata =
            AnonCredsCredentialRequestMetadata.fromJsonString(credentialRequestMetadata.toJson())

        return CreateCredentialRequestReturn(
            credentialRequest = anonCredsCredentialRequest,
            credentialRequestMetadata = anonCredsCredentialRequestMetadata,
        )

//        } finally {
// //            createReturnObj.request.handle.clear()
// //            createReturnObj.metadata.handle.clear()
//        }
    }

    override suspend fun deleteCredential(credentialId: String) {
        val w3cCredentialRecord = agent.w3cCredentialRepository.findById(credentialId)

        if (w3cCredentialRecord != null) {
            agent.w3cCredentialRepository.delete(w3cCredentialRecord)
            return
        }

        val anoncredsCredentialRecord =
            agent.anonCredsCredentialRepository.getByCredentialId(credentialId)
        agent.anonCredsCredentialRepository.delete(anoncredsCredentialRecord)
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
    ): AnonCredsCredentialInfo { // can be W3cCredentialRecord | AnonCredsCredentialRecord
        if (credentialRecord is W3cCredentialRecord) {
            logger.info("credentialRecord is W3cCredentialRecord")
            return W3cAnonCredsUtils.anonCredsCredentialInfoFromW3cRecord(
                credentialRecord,
                useUnqualifiedIdentifiersIfPresent,
            )
        } else {
            logger.info("credentialRecord is not W3cCredentialRecord")
            return W3cAnonCredsUtils.anonCredsCredentialInfoFromAnonCredsRecord(credentialRecord as AnonCredsCredentialRecord)
        }
    }

    private suspend fun storeLinkSecret(options: StoreLinkSecretOptions): AnonCredsLinkSecretRecord {
        val (linkSecretId, linkSecretValue, setAsDefault) = options
        val linkSecretRecord =
            AnonCredsLinkSecretRecord(linkSecretId = linkSecretId, value = linkSecretValue)

        val defaultLinkSecretRecord = agent.anonCredsLinkSecretRepository.findDefault()
        if (defaultLinkSecretRecord == null || setAsDefault) {
            linkSecretRecord.setTag("isDefault", true.toString())
        }

        if (defaultLinkSecretRecord != null && setAsDefault) {
            defaultLinkSecretRecord.setTag("isDefault", false.toString())
            agent.anonCredsLinkSecretRepository.update(defaultLinkSecretRecord)
        }

        agent.anonCredsLinkSecretRepository.save(linkSecretRecord)

        return linkSecretRecord
    }

    /**
     * Gera um nonce de 80 bits adequado para provas AnonCreds
     */
    fun generateNonce(): String {
        val random = SecureRandom()
        val bytes = ByteArray(10) // 80 bits
        random.nextBytes(bytes)

        // Constrói BigInteger a partir dos bytes (positivo)
        val nonce = bytes.fold(BigInteger.ZERO) { acc, byte ->
            (acc shl 8) or BigInteger.valueOf(byte.toLong() and 0xFF)
        }

        return nonce.toString()
    }

    private fun toJsonObject(obj: Any): JsonObject {
        val jsonElement: JsonElement = Json.encodeToJsonElement(obj)
        return jsonElement.jsonObject
    }

    private fun queryFromRestrictions(
        restrictions: List<AnonCredsProofRequestRestriction>,
    ): MutableMap<String, String> {
        val queries = mutableListOf<Map<String, String>>()

        for (restriction in restrictions) {
            val q = mutableMapOf<String, String>()

            // credentialDefinitionId
            restriction.credDefId?.let { cdId ->
                if (Indyidentifiers.isUnqualifiedCredentialDefinitionId(cdId)) {
                    q["anonCredsUnqualifiedCredentialDefinitionId"] = cdId
                } else {
                    q["anonCredsCredentialDefinitionId"] = cdId
                }
            }

            // issuerId / issuerDid
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

            // schemaId
            restriction.schemaId?.let { scId ->
                if (Indyidentifiers.isUnqualifiedSchemaId(scId)) {
                    q["anonCredsUnqualifiedSchemaId"] = scId
                } else {
                    q["anonCredsSchemaId"] = scId
                }
            }

            // schemaIssuerId / schemaIssuerDid
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

            // schemaName / schemaVersion
            restriction.schemaName?.let { q["anonCredsSchemaName"] = it }
            restriction.schemaVersion?.let { q["anonCredsSchemaVersion"] = it }

            // attributeValues -> anonCredsAttr::<name>::value
            for ((attrName, attrValue) in restriction.attributeValues) {
                q["anonCredsAttr::$attrName::value"] = attrValue
            }

            // attributeMarkers -> anonCredsAttr::<name>::marker = true (quando true)
            for ((attrName, isAvailable) in restriction.attributeMarkers) {
                if (isAvailable) {
                    q["anonCredsAttr::$attrName::marker"] = true.toString()
                }
            }

            queries += q
        }

        return queries.first().toMutableMap()
//        return if (queries.size == 1) {
//            queries.first()
//        } else {
//            mapOf("\$or" to queries)
//        }
    }

    private suspend fun getLegacyCredentialsForProofRequest(
        options: GetCredentialsForProofRequestOptions,
    ): GetCredentialsForProofRequestReturn {
        val proofRequest = options.proofRequest
        val referent = options.attributeReferent

        val requestedAttribute = proofRequest.requestedAttributes[referent]
            ?: proofRequest.requestedPredicates[referent]
            ?: throw AnonCredsRsError("Referent not found in proof request")

        val andClauses = mutableListOf<Any>()

        val attributes: List<String> =
            when (requestedAttribute) {
                is AnonCredsRequestedAttribute ->
                    requestedAttribute.names ?: listOfNotNull(requestedAttribute.name)

                is AnonCredsRequestedPredicate -> {
                    listOfNotNull(requestedAttribute.name)
                }

                else -> error("Tipo inesperado para requested referent")
            }

        val attributeQuery = mutableMapOf<String, Any?>()
        attributes.forEach { attr ->
            attributeQuery["anonCredsAttr::$attr::marker"] = true
        }
        andClauses += attributeQuery

        // restrições do proof request (legacy)
        when (requestedAttribute) {
            is AnonCredsRequestedAttribute -> {
                val restrictions = requestedAttribute.restrictions
                if (!restrictions.isNullOrEmpty()) {
                    val restrictionQuery = queryLegacyFromRestrictions(restrictions)
                    andClauses += restrictionQuery
                }
            }

            is AnonCredsRequestedPredicate -> {
                val restrictions = requestedAttribute.restrictions
                if (!restrictions.isNullOrEmpty()) {
                    val restrictionQuery = queryLegacyFromRestrictions(restrictions)
                    andClauses += restrictionQuery
                }
            }
        }

        options.extraQuery?.let { andClauses += it }

        // No final:
        val finalQuery: Map<String, Any?> = mapOf("\$and" to andClauses)
        val credentials = agent.anonCredsCredentialRepository.findByQuery(finalQuery.toString())

        val credentialForProofRequestList: List<CredentialForProofRequest> =
            credentials.map { credentialRecord ->
                CredentialForProofRequest(
                    credentialInfo = getAnoncredsCredentialInfoFromRecord(credentialRecord),
                    interval = proofRequest.nonRevoked,
                )
            }

        return GetCredentialsForProofRequestReturn(
            credentials = credentialForProofRequestList,
        )
    }

    private fun queryLegacyFromRestrictions(
        restrictions: List<AnonCredsProofRequestRestriction>,
    ): Map<String, Any?> {
        val queries = mutableListOf<Map<String, Any?>>()

        for (restriction in restrictions) {
            val queryElements = mutableMapOf<String, Any?>()
            val additionalQueryElements = mutableMapOf<String, Any?>()

            // credentialDefinitionId
            restriction.credDefId?.let { cdId ->
                queryElements["credentialDefinitionId"] = cdId
                if (Indyidentifiers.isUnqualifiedCredentialDefinitionId(cdId)) {
                    additionalQueryElements["credentialDefinitionId"] = cdId
                }
            }

            // issuerId / issuerDid
            run {
                val issuerId = restriction.issuerId ?: restriction.issuerDid
                if (issuerId != null) {
                    queryElements["issuerId"] = issuerId
                    if (Indyidentifiers.isUnqualifiedIndyDid(issuerId)) {
                        additionalQueryElements["issuerId"] = issuerId
                    }
                }
            }

            // schemaId
            restriction.schemaId?.let { scId ->
                queryElements["schemaId"] = scId
                if (Indyidentifiers.isUnqualifiedSchemaId(scId)) {
                    additionalQueryElements["schemaId"] = scId
                }
            }

            // schemaIssuerId / schemaIssuerDid
            run {
                val schemaIssuerId = restriction.schemaIssuerId ?: restriction.schemaIssuerDid
                if (schemaIssuerId != null) {
                    queryElements["schemaIssuerId"] = schemaIssuerId
                    if (Indyidentifiers.isUnqualifiedIndyDid(schemaIssuerId)) {
                        additionalQueryElements["schemaIssuerId"] = schemaIssuerId
                    }
                }
            }

            // schemaName / schemaVersion
            restriction.schemaName?.let { queryElements["schemaName"] = it }
            restriction.schemaVersion?.let { queryElements["schemaVersion"] = it }

            // attributeValues -> "attr::<name>::value"
            for ((attributeName, attributeValue) in restriction.attributeValues) {
                queryElements["attr::$attributeName::value"] = attributeValue
            }

            // attributeMarkers (true) -> "attr::<name>::marker"
            for ((attributeName, isAvailable) in restriction.attributeMarkers) {
                if (isAvailable) {
                    queryElements["attr::$attributeName::marker"] = true
                }
            }

            queries += queryElements
            if (additionalQueryElements.isNotEmpty()) {
                queries += additionalQueryElements
            }
        }

        return if (queries.size == 1) {
            queries.first()
        } else {
            mapOf("\$or" to queries)
        }
    }
}
