package org.hyperledger.ariesframework.proofs.verifier

import anoncreds_uniffi.Presentation
import kotlinx.serialization.json.JsonObject
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProof
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequestRestriction
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedAttribute
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedPredicate
import org.hyperledger.ariesframework.proofs.models.NonRevokedIntervalOverride
import org.hyperledger.ariesframework.proofs.models.RequestedItem
import org.hyperledger.ariesframework.proofs.models.TimestampVerificationResult
import org.slf4j.LoggerFactory
import uniffi.indy_besu_vdr.RevocationStatusList

class AnonCredsRsVerifierService(val agent: Agent) : AnonCredsVerifierService {
    private val logger = LoggerFactory.getLogger(AnonCredsRsVerifierService::class.java)

    override suspend fun verifyProof(
        options: VerifyProofOptions,
    ): Boolean {
        val (proofRequest, proof, schemas, credentialDefinitions, revocationRegistries) = options

        var presentation: Presentation? = null

        val (verified, nonRevokedIntervalOverrides) =
            verifyTimestamps(proof, proofRequest)

        if (!verified) {
            logger.debug("Invalid timestamps for provided identifiers")
            return false
        }

        presentation = Presentation(proof.toString())

        val rsCredentialDefinitions = mutableMapOf<String, JsonObject>()
        for ((credDefId, value) in credentialDefinitions.credentialDefinitions) {
            rsCredentialDefinitions[credDefId] = value as JsonObject
        }

        val rsSchemas = mutableMapOf<String, JsonObject>()
        for ((schemaId, value) in schemas.schemas) {
            rsSchemas[schemaId] = value as JsonObject
        }

        val revocationRegistryDefinitions = mutableMapOf<String, JsonObject>()
        val lists = mutableListOf<JsonObject>()

        for ((revRegDefId, reg) in revocationRegistries) {
            val definition = reg.definition as JsonObject
            revocationRegistryDefinitions[revRegDefId] = definition

            val revocationStatusLists = reg.revocationStatusLists?.values
            if (revocationStatusLists != null) {
                for (lst in revocationStatusLists) {
                    lists.add(lst as JsonObject)
                }
            }
        }

//        return presentation.verify(
//            mapOf(
//                "presentationRequest" to (proofRequest as JsonObject),
//                "credentialDefinitions" to rsCredentialDefinitions,
//                "schemas" to rsSchemas,
//                "revocationRegistryDefinitions" to revocationRegistryDefinitions,
//                "revocationStatusLists" to lists,
//                "nonRevokedIntervalOverrides" to nonRevokedIntervalOverrides
//            )
//        )
        return false
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
