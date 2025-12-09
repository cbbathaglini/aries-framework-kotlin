package org.hyperledger.ariesframework.proofs.v2.verifier

import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.Presentation
import anoncreds_uniffi.PresentationRequest
import anoncreds_uniffi.RevocationRegistryDefinition
import anoncreds_uniffi.Schema
import anoncreds_uniffi.Verifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
import org.hyperledger.ariesframework.util.PrintLongLine
import org.hyperledger.ariesframework.util.concurrentForEach
import org.slf4j.LoggerFactory
import uniffi.indy_besu_vdr.RevocationStatusList
import kotlin.collections.iterator
import kotlin.collections.set

class AnonCredsRsVerifierService(val agent: Agent) : AnonCredsVerifierService {
    private val logger = LoggerFactory.getLogger(AnonCredsRsVerifierService::class.java)

    override suspend fun verifyProof(
        options: VerifyProofOptions,
    ): Boolean {
        val (proofRequest, presentationMessage, requestMessage, proof, schemas, credentialDefinitions, _) = options

        logger.info(">>> verifyProof() options = $options")

        // --- Decodificar a presentation para pegar identifiers (revRegId / timestamp) ---
        val proofJson = presentationMessage.anoncredsProof()
        val partialProof = Json { ignoreUnknownKeys = true }
            .decodeFromString<PartialProof>(proofJson)

        logger.info(">>> verifyProof() partialProof = $partialProof")
        logger.info(">>> verifyProof() partialProof = ${partialProof.identifiers}")

        val identifiers = partialProof.identifiers
        if (identifiers.isEmpty()) {
            logger.error(">>> Nenhum identifier na prova – retornando false")
            return false
        }

        val identifier = identifiers.first()
        val holderTimestamp: Int? = identifier.timestamp
        val revRegId = identifier.revocationRegistryId

        logger.error(">>> VERIFIER identifier.revRegId  = $revRegId")
        logger.error(">>> VERIFIER identifier.timestamp = $holderTimestamp")

        val presentationRequest = PresentationRequest(requestMessage.anoncredsProofRequest())
        logger.error(">>> VERIFIER nonce = ${presentationRequest.toJson()}")
        val presentation = Presentation(proofJson)

        val proofUniffi = presentation.proof()
        val aggr = proofUniffi.aggregatedProof

        PrintLongLine.print("VERIFIER PRESENTATION.PROOF- $proofUniffi")
        PrintLongLine.print("VERIFIER AGGREGATED- $aggr")

        val schemaIds: Set<String> = schemas.schemas.keys
        val schemasAnoncreds: Map<String, Schema> =
            RecoverFromLedger.getSchemas(schemaIds, agent)

        val credDefIds: Set<String> = credentialDefinitions.credentialDefinitions.keys
        val credDefsAnoncreds: Map<String, CredentialDefinition> =
            RecoverFromLedger.getCredentialDefinitions(credDefIds, agent)

        // ==== CASO SEM REVOGAÇÃO ======================================================
        if (holderTimestamp == null || revRegId == null) {
            logger.info(">>> Prova NÃO usa revogação (timestamp ou revRegId nulos)")
            return try {
                Verifier().verifyPresentation(
                    presReq = presentationRequest,
                    schemas = schemasAnoncreds,
                    credDefs = credDefsAnoncreds,
                    revRegDefs = null,
                    revStatusLists = null,
                    presentation = presentation,
                    nonrevokeIntervalOverride = null,
                )
            } catch (e: Exception) {
                logger.error(">>> ERRO verificando prova SEM revogação: $e")
                false
            }
        }

        // ==== CASO COM REVOGAÇÃO ======================================================
        val ts: ULong = holderTimestamp.toULong()

        // (1) RevocationRegistryDefinition do ledger
        val revRegDefJson = agent.ledgerService.getRevocationRegistryDefinition(revRegId!!)
        val revRegDefUni = RevocationRegistryDefinition(revRegDefJson)
        val revRegDefsMap = mapOf(revRegId to revRegDefUni)

        logger.error(">>> VERIFIER RevocationRegistryDefinition JSON = ${revRegDefUni.toJson()}")

        val ledgerStatusList =
            agent.ledgerService.getRevocationStatusList(revRegId, ts)

        logger.error(">>> VERIFIER LEDGER STATUS LIST (raw Kotlin) <<<")
        logger.error("issuerId           = ${ledgerStatusList.issuerId}")
        logger.error("revRegDefId        = ${ledgerStatusList.revRegDefId}")
        logger.error("timestamp (raw)    = ${ledgerStatusList.timestamp}")
        logger.error("currentAccumulator = ${ledgerStatusList.currentAccumulator}")
        logger.error("revList size       = ${ledgerStatusList.revocationList.size}")

//        val credRevIndex = identifier.cre  // <-- nome correto
//        logger.error(">>> ver revRegIndex = $credRevIndex")
//
//        logger.error(
//            "revList[$credRevIndex] = ${
//                ledgerStatusList.revocationList
//
//            }"
//        )

        // (3) Converter Besu -> JSON compatível com anoncreds_uniffi.RevocationStatusList,
        //     usando EXATAMENTE o revRegId e o holderTimestamp do proof
        val statusListJson = indyBesuRevocationStatusListToJson(
            src = ledgerStatusList,
            revRegDefId = revRegId,
            targetTimestamp = ts,
        )

        val statusListUniffi = anoncreds_uniffi.RevocationStatusList(statusListJson)

        logger.error(">>> VERIFIER STATUS LIST (UNIFFI JSON) <<<")
        val statusListUniffiJson = statusListUniffi.toJson()
        logger.error(statusListUniffiJson)

        try {
            val parsed = Json.parseToJsonElement(statusListUniffiJson).jsonObject
            logger.error("VERIFIER UNIFFI revRegDefId = ${parsed["revRegDefId"]}")
            logger.error("VERIFIER UNIFFI timestamp   = ${parsed["timestamp"]}")
        } catch (e: Exception) {
            logger.error("ERRO parseando UNIFFI statuslist no verifier: $e")
        }

        // (4) Chamar o verifier do anoncreds-rs
        return try {
            val verified = Verifier().verifyPresentation(
                presReq = presentationRequest,
                schemas = schemasAnoncreds,
                credDefs = credDefsAnoncreds,
                revRegDefs = revRegDefsMap,
                revStatusLists = listOf(statusListUniffi),
                presentation = presentation,
                nonrevokeIntervalOverride = null,
            )

            logger.error(">>> VERIFIER RESULTADO FINAL = $verified")
            verified
        } catch (e: Exception) {
            logger.error(">>> ERRO VERIFICANDO PROVA: $e")
            false
        }
    }

    private fun indyBesuRevocationStatusListToJson(
        src: RevocationStatusList,
        revRegDefId: String,
        targetTimestamp: ULong,
    ): String {
        // Converter List<UInt> -> List<Int> (0/1)
        val listAsInt: List<Int> = src.revocationList.map { it.toInt() }

        val revocationListJson = Json.encodeToString(listAsInt)

        logger.error(">>> indyBesuRevocationStatusListToJson()")
        logger.error("src.revRegDefId   = ${src.revRegDefId}")
        logger.error("param.revRegDefId = $revRegDefId")
        logger.error("src.timestamp     = ${src.timestamp}")
        logger.error("targetTimestamp   = $targetTimestamp")
        logger.error("revList.size      = ${listAsInt.size}")
        logger.error("revList[0..5]     = ${listAsInt.take(6)}")

        // IMPORTANTE: usamos SEMPRE o revRegDefId e timestamp do proof
        return """
        {
          "issuerId": "${src.issuerId}",
          "revRegDefId": "$revRegDefId",
          "revocationList": $revocationListJson,
          "currentAccumulator": "${src.currentAccumulator}",
          "timestamp": $targetTimestamp
        }
        """.trimIndent()
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
            if (requestedFrom != null && requestedFrom > timestamp.toULong()) {
                // Consulta VDR para checar se a lista ativa em requestedFrom equivale ao timestamp informado
                val revocationStatusList: RevocationStatusList = agent.ledgerService.getRevocationStatusList(
                    id = revRegId,
                    timestamp = requestedFrom,
                )

                val vdrTimestamp = revocationStatusList.timestamp
                if (timestamp != null && vdrTimestamp == timestamp.toULong()) {
                    nonRevokedIntervalOverrides += NonRevokedIntervalOverride(
                        overrideRevocationStatusListTimestamp = timestamp.toULong(),
                        requestedFromTimestamp = requestedFrom.toULong(),
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
