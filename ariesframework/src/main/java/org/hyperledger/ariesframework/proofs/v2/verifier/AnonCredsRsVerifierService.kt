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
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProof
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedAttribute
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedPredicate
import org.hyperledger.ariesframework.proofs.models.NonRevokedIntervalOverride
import org.hyperledger.ariesframework.proofs.models.PartialProof
import org.hyperledger.ariesframework.proofs.models.RequestedItem
import org.hyperledger.ariesframework.proofs.models.TimestampVerificationResult
import org.hyperledger.ariesframework.proofs.utils.RecoverFromLedger
import org.hyperledger.ariesframework.util.LogUtil
import org.hyperledger.ariesframework.util.concurrentForEach
import uniffi.indy_besu_vdr.RevocationStatusList

class AnonCredsRsVerifierService(val agent: Agent) : AnonCredsVerifierService {

    override suspend fun verifyProof(options: VerifyProofOptions): Boolean {
        LogUtil.info(this) { "verifying proof" }
        val (proofRequest, presentationMessage, requestMessage, proof, schemas, credentialDefinitions, _) = options

        val proofJson = presentationMessage.anoncredsProof()
        val partialProof = Json { ignoreUnknownKeys = true }
            .decodeFromString<PartialProof>(proofJson)

        val identifiers = partialProof.identifiers
        if (identifiers.isEmpty()) {
            return false
        }

        val identifier = identifiers.first()
        val holderTimestamp: Int? = identifier.timestamp
        val revRegId = identifier.revocationRegistryId

        val presentationRequest = PresentationRequest(requestMessage.anoncredsProofRequest())

        val presentation = Presentation(proofJson)
        val proofUniffi = presentation.proof()
        val aggregated = proofUniffi.aggregatedProof

        // PrintLongLine.print("VERIFIER PRESENTATION.PROOF - $proofUniffi")
        // PrintLongLine.print("VERIFIER AGGREGATED - $aggregated")

        val schemaIds: Set<String> = schemas.schemas.keys
        val schemasAnoncreds: Map<String, Schema> = RecoverFromLedger.getSchemas(schemaIds, agent)

        val credDefIds: Set<String> = credentialDefinitions.credentialDefinitions.keys
        val credDefsAnoncreds: Map<String, CredentialDefinition> =
            RecoverFromLedger.getCredentialDefinitions(credDefIds, agent)

        if (holderTimestamp == null || revRegId == null) {
            // logger.info("Proof does not use revocation")
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
                LogUtil.error(this, e) { "Error verifying non-revoked proof: ${e.message}" }
                // logger.error("Error verifying non-revoked proof: $e")
                false
            }
        }

        val ts: ULong = holderTimestamp.toULong()

        val revRegDefJson = agent.ledgerService.getRevocationRegistryDefinition(revRegId)
        val revRegDefUni = RevocationRegistryDefinition(revRegDefJson)
        val revRegDefsMap = mapOf(revRegId to revRegDefUni)

        val ledgerStatusList = agent.ledgerService.getRevocationStatusList(revRegId, ts)
        val statusListJson = indyBesuRevocationStatusListToJson(
            src = ledgerStatusList,
            revRegDefId = revRegId,
            targetTimestamp = ts,
        )

        val statusListUniffi = anoncreds_uniffi.RevocationStatusList(statusListJson)

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

            // logger.error("Final verification result = $verified")
            verified
        } catch (e: Exception) {
            // logger.error("Error verifying revoked proof: $e")
            LogUtil.error(this, e) { "Error verifying revoked proof: ${e.message}" }
            false
        }
    }

    private fun indyBesuRevocationStatusListToJson(
        src: RevocationStatusList,
        revRegDefId: String,
        targetTimestamp: ULong,
    ): String {
        val listAsInt: List<Int> = src.revocationList.map { it.toInt() }
        val revocationListJson = Json.encodeToString(listAsInt)

        // logger.error("Converting Besu revocation list to UNIFFI JSON")

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

    suspend fun getRevocationRegistryDefinitions(
        revocationRegistryIds: Set<String>,
    ): Map<String, RevocationRegistryDefinition> {
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
        val globalNonRevokedInterval = proofRequest.nonRevoked
        val requestedNonRevokedRestrictions = mutableListOf<RequestedItem>()

        val allRequestedValues = buildList {
            addAll(proofRequest.requestedAttributes.values)
            addAll(proofRequest.requestedPredicates.values)
        }

        for (value in allRequestedValues) {
            val nonRevokedInterval =
                (value as? AnonCredsRequestedAttribute)?.nonRevoked
                    ?: (value as? AnonCredsRequestedPredicate)?.nonRevoked
                    ?: globalNonRevokedInterval

            if (nonRevokedInterval != null) {
                val restrictions =
                    (value as? AnonCredsRequestedAttribute)?.restrictions
                        ?: (value as? AnonCredsRequestedPredicate)?.restrictions

                restrictions?.forEach { restriction ->
                    requestedNonRevokedRestrictions += RequestedItem(
                        nonRevokedInterval = nonRevokedInterval,
                        schemaId = restriction.schemaId,
                        credentialDefinitionId = restriction.credDefId,
                        revocationRegistryDefinitionId = restriction.revRegId,
                    )
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
                val revocationStatusList: RevocationStatusList =
                    agent.ledgerService.getRevocationStatusList(
                        id = revRegId,
                        timestamp = requestedFrom,
                    )

                val vdrTimestamp = revocationStatusList.timestamp
                if (timestamp.toULong() == vdrTimestamp) {
                    nonRevokedIntervalOverrides += NonRevokedIntervalOverride(
                        overrideRevocationStatusListTimestamp = timestamp.toULong(),
                        requestedFromTimestamp = requestedFrom.toULong(),
                        revocationRegistryDefinitionId = revRegId,
                    )
                } else {
                    // logger.debug("VDR timestamp does NOT match the presented timestamp")
                    return TimestampVerificationResult(false)
                }
            }
        }

        return TimestampVerificationResult(
            verified = true,
            nonRevokedIntervalOverrides = nonRevokedIntervalOverrides.takeIf { it.isNotEmpty() },
        )
    }
}
