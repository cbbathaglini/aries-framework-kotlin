package org.hyperledger.ariesframework.proofs.verifier

import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.Presentation
import anoncreds_uniffi.PresentationRequest
import anoncreds_uniffi.RevocationRegistryDefinition
import anoncreds_uniffi.Schema
import anoncreds_uniffi.Verifier
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProof
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequestRestriction
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedAttribute
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedPredicate
import org.hyperledger.ariesframework.proofs.models.NonRevokedIntervalOverride
import org.hyperledger.ariesframework.proofs.models.PartialProof
import org.hyperledger.ariesframework.proofs.models.RequestedItem
import org.hyperledger.ariesframework.proofs.models.TimestampVerificationResult
import org.hyperledger.ariesframework.proofs.utils.RecoverFromLedger
import org.hyperledger.ariesframework.util.concurrentForEach
import org.slf4j.LoggerFactory
import uniffi.indy_besu_vdr.RevocationStatusList
import kotlin.collections.set

class AnonCredsRsVerifierService(val agent: Agent) : AnonCredsVerifierService {
    private val logger = LoggerFactory.getLogger(AnonCredsRsVerifierService::class.java)

    override suspend fun verifyProof(
        options: VerifyProofOptions,
    ): Boolean = coroutineScope {
        val (proofRequest, presentationMessage, requestMessage, proof, schemas, credentialDefinitions, revocationRegistries) = options
        logger.info("oprions: $options")
        logger.info("proofRequest: $proofRequest")

        var presentation: Presentation? = null

        val (verified, nonRevokedIntervalOverrides) =
            verifyTimestamps(proof, proofRequest)

        if (!verified) {
            logger.debug("Invalid timestamps for provided identifiers")
            return@coroutineScope false
        }

        presentation = Presentation(presentationMessage.anoncredsProof())
        logger.info("presentation: $presentation")

        val credentialDefinitionIds: Set<String> = credentialDefinitions.credentialDefinitions.keys
        val credentialDefinitionAnoncreds: Map<String, CredentialDefinition> = RecoverFromLedger.getCredentialDefinitions(credentialDefinitionIds, agent)

        val schemaIds: Set<String> = schemas.schemas.keys
        val schemasAnoncreds: Map<String, Schema> = RecoverFromLedger.getSchemas(schemaIds, agent)

        val revRegDefIds: Set<String> = revocationRegistries.keys
        val revocationRegistryDefinitions: Map<String, RevocationRegistryDefinition> = getRevocationRegistryDefinitions(revRegDefIds)

        val proofAnoncreds = presentationMessage.anoncredsProof()
        val proofRequestAnoncreds = requestMessage.anoncredsProofRequest()
        val partialProofObj = Json { ignoreUnknownKeys = true }.decodeFromString<PartialProof>(proofAnoncreds)
        val revocationStatusLists: List<anoncreds_uniffi.RevocationStatusList> =
            agent.revocationService.getRevocationStatusLists(
                proof = partialProofObj,
                revocationRegistryDefinitions = revocationRegistryDefinitions,
            )

        return@coroutineScope try {
            Verifier().verifyPresentation(
                presReq = PresentationRequest(proofRequestAnoncreds),
                schemas = schemasAnoncreds,
                credDefs = credentialDefinitionAnoncreds,
                revRegDefs = revocationRegistryDefinitions,
                revStatusLists = revocationStatusLists,
                presentation = presentation,
                nonrevokeIntervalOverride = null,
            )
        } catch (e: Exception) {
            logger.error("Error verifying proof: $e")
            false
        }
    }

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

    override suspend fun verifyW3cPresentation(options: VerifyW3cPresentationOptions): Boolean {
        return false
    }

    private suspend fun verifyTimestamps(
        proof: AnonCredsProof,
        proofRequest: AnonCredsProofRequest,
    ): TimestampVerificationResult {
        val nonRevokedIntervalOverrides = mutableListOf<NonRevokedIntervalOverride>()

        // Intervalo global
        val globalNonRevokedInterval = proofRequest.nonRevoked

        val requestedNonRevokedRestrictions = mutableListOf<RequestedItem>()

        // Agrega restrições de atributos e predicados
        val allRequestedValues = buildList {
            addAll(proofRequest.requestedAttributes.values)
            addAll(proofRequest.requestedPredicates.values)
        }

        for (value in allRequestedValues) {
            val nonRevokedInterval = when (value) {
                is AnonCredsRequestedAttribute -> value.nonRevoked
                is AnonCredsRequestedPredicate -> value.nonRevoked
                else -> globalNonRevokedInterval
            }

            if (nonRevokedInterval != null) {
                val restrictions = when (value) {
                    is AnonCredsRequestedAttribute -> value.restrictions
                    is AnonCredsRequestedPredicate -> value.restrictions
                    else -> emptyList<AnonCredsProofRequestRestriction>()
                }

                if (restrictions != null) {
                    for (restriction in restrictions) {
                        requestedNonRevokedRestrictions += RequestedItem(
                            nonRevokedInterval = nonRevokedInterval,
                            schemaId = restriction.schemaId,
                            credentialDefinitionId = restriction.credDefId,
                            revocationRegistryDefinitionId = restriction.revRegId,
                        )
                    }
                }
            }
        }

        for (identifier in proof.identifiers) {
            val timestamp = identifier.timestamp
            val revRegId = identifier.revRegId
            if (timestamp == null || revRegId == null) continue

            val related = requestedNonRevokedRestrictions.firstOrNull { item ->
                item.revocationRegistryDefinitionId == revRegId ||
                    item.credentialDefinitionId == identifier.credDefId ||
                    item.schemaId == identifier.schemaId
            }

            val requestedFrom = related?.nonRevokedInterval?.from
            if (requestedFrom != null && requestedFrom > timestamp) {
                // Consulta VDR para checar se a lista ativa em requestedFrom equivale ao timestamp informado
                val revocationStatusList: RevocationStatusList = agent.ledgerService.getRevocationStatusList(
                    id = revRegId,
                    timestamp = requestedFrom.toInt(),
                )

                val vdrTimestamp = revocationStatusList.timestamp
                if (timestamp != null && vdrTimestamp == timestamp.toULong()) {
                    nonRevokedIntervalOverrides += NonRevokedIntervalOverride(
                        overrideRevocationStatusListTimestamp = timestamp,
                        requestedFromTimestamp = requestedFrom,
                        revocationRegistryDefinitionId = revRegId,
                    )
                } else {
                    logger.debug(
                        "VDR timestamp for $requestedFrom does not correspond to the one provided in proof identifiers. " +
                            "Expected: $timestamp and received $vdrTimestamp",
                    )
                    return TimestampVerificationResult(verified = false)
                }
            }
        }

        return TimestampVerificationResult(
            verified = true,
            nonRevokedIntervalOverrides = nonRevokedIntervalOverrides.takeIf { it.isNotEmpty() },
        )
    }

//    private suspend fun getRevocationMetadataForCredentials(
//        credentialsWithMetadata: List<CredentialWithRevocationMetadata>
//    ): List<RevocationMetadata>{
//        val tasks = credentialsWithMetadata
//            .filter { it.nonRevoked != null }
//            .map { credentialWithMetadata ->
//                val w3cJsonLdVerifiableCredential = JsonTransformer.toJSON(credentialWithMetadata.credential)
//                val anonCreds = AnonCredsW3cCredential.fromJson(w3cJsonLdVerifiableCredential)
//
//                getRevocationMetadata(
//                    params = GetRevocationMetadataParams(
//                        nonRevokedInterval = credentialWithMetadata.nonRevoked as AnonCredsNonRevokedInterval,
//                        timestamp = anonCreds.timestamp,
//                        revocationRegistryId = anonCreds.revocationRegistryId,
//                        revocationRegistryIndex = anonCreds.revocationRegistryIndex
//                    )
//                )
//            }
//
//        tasks.awaitAllIfDeferredOrJustCollect()
//    }

//    override suspend fun verifyW3cPresentation(
//        options: VerifyW3cPresentationOptions
//    ): Boolean {
//        val revocationMetadata = getRevocationMetadataForCredentials(
//            options.credentialsWithRevocationMetadata
//        )
//
//        val revocationRegistryDefinitions = mutableMapOf<String, RevocationRegistryDefinition>()
//        for (rm in revocationMetadata) {
//            revocationRegistryDefinitions[rm.revocationRegistryId] = rm.revocationRegistryDefinition
//        }
//
//        val verificationOptions = VerifyAnonCredsW3cPresentationOptions(
//            presentationRequest = options.proofRequest as JsonObject,
//            schemas = options.schemas as Map<String, JsonObject>,
//            credentialDefinitions = options.credentialDefinitions as Map<String, JsonObject>,
//            revocationRegistryDefinitions = revocationRegistryDefinitions,
//            revocationStatusLists = revocationMetadata.map { it.revocationStatusList },
//            nonRevokedIntervalOverrides = revocationMetadata
//                .mapNotNull { it.nonRevokedIntervalOverride }
//        )
//
//        var result = false
//        val presentationJson = JsonTransformer.toJSON(options.presentation).toMutableMap()
//        if (presentationJson.containsKey("presentation_submission")) {
//            presentationJson["presentation_submission"] = null
//        }
//
//        var w3cPresentation: W3cPresentation? = null
//        try {
//            w3cPresentation = W3cPresentation.fromJson(presentationJson)
//            result = w3cPresentation.verify(verificationOptions)
//        } finally {
//            w3cPresentation?.handle?.clear()
//        }
//        result
//    }
}
