package org.hyperledger.ariesframework.anoncreds.formats.utils

import anoncreds_uniffi.RevocationRegistryDefinition
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsRequestedAttributeMatch
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsRequestedPredicateMatch
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsSelectedCredentials
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProof
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryEntry
import org.hyperledger.ariesframework.anoncreds.model.FetchIntermediateRevocationRegistryDefinitionResult
import org.hyperledger.ariesframework.anoncreds.model.ReferentItem
import org.hyperledger.ariesframework.anoncreds.model.RevocationRegistriesForRequestResult
import org.hyperledger.ariesframework.anoncreds.model.RevocationRegistryBucket
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.proofs.verifier.RevocationRegistryEntry
import org.slf4j.LoggerFactory

data class RevocationRegistries (val agent: Agent){

    private val logger = LoggerFactory.getLogger(RevocationRegistries::class.java)
//    suspend fun getRevocationRegistriesForRequest(
//        proofRequest: AnonCredsProofRequest,
//        selectedCredentials: AnonCredsSelectedCredentials
//    ): RevocationRegistriesForRequestResult = coroutineScope {
//        val revocationRegistries = mutableMapOf<String, AnonCredsRevocationRegistryEntry>()
//
//        // Não mutar o objeto recebido
//        var updatedSelectedCredentials = selectedCredentials
//
//        try {
//           logger.debug(
//                "Retrieving revocation registries for proof request",
//                mapOf("proofRequest" to proofRequest, "selectedCredentials" to selectedCredentials)
//            )
//
//
//            val referentCredentials = mutableListOf<ReferentItem>()
//
//            for ((referent, sel) in selectedCredentials.attributes) {
//                referentCredentials += ReferentItem(
//                    type = ReferentItem.Type.ATTRIBUTES,
//                    referent = referent,
//                    selectedCredential = sel,
//                    nonRevoked = proofRequest.requestedAttributes[referent]?.nonRevoked ?: proofRequest.nonRevoked
//                )
//            }
//            for ((referent, sel) in selectedCredentials.predicates) {
//                referentCredentials += ReferentItem(
//                    type = ReferentItem.Type.PREDICATES,
//                    referent = referent,
//                    selectedCredential = sel,
//                    nonRevoked = proofRequest.requestedPredicates[referent]?.nonRevoked ?: proofRequest.nonRevoked
//                )
//            }
//
//            // Para aplicar timestamps após o fetch concorrente
//            data class TimestampUpdate(val type: ReferentItem.Type, val referent: String, val timestamp: Long)
//
//            val timestampUpdates = mutableListOf<TimestampUpdate>()
//
//            // Promessas concorrentes (async) de obtenção de registries/status lists
//            referentCredentials
//                .map { item ->
//                    async {
//                        val selected = when (item.type) {
//                            ReferentItem.Type.ATTRIBUTES -> item.selectedCredential as AnonCredsRequestedAttributeMatch
//                            ReferentItem.Type.PREDICATES -> item.selectedCredential as AnonCredsRequestedPredicateMatch
//                        }
//
//                        val credentialInfo = when (selected) {
//                            is AnonCredsRequestedAttributeMatch -> selected.credentialInfo
//                            is AnonCredsRequestedPredicateMatch -> selected.credentialInfo
//                            else -> throw IllegalArgumentException("Tipo inválido: ${selected::class}")
//                        }
//
//                        if (credentialInfo == null) {
//                            throw CredoError("Credential for referent '${item.referent} does not have credential info for revocation state creation")
//                        }
//
//                        val credentialRevocationId = credentialInfo.credentialRevocationId
//                        val revocationRegistryId = credentialInfo.revocationRegistryId
//                        val timestamp = when (item.type) {
//                            ReferentItem.Type.ATTRIBUTES -> (item.selectedCredential as AnonCredsRequestedAttributeMatch).timestamp
//                            ReferentItem.Type.PREDICATES -> (item.selectedCredential as AnonCredsRequestedPredicateMatch).timestamp
//                        }
//
//                        val nonRevoked = item.nonRevoked
//
//                        // Só cria estado se: há intervalo, cred é revogável e há registry id
//                        if (nonRevoked != null && credentialRevocationId != null && !revocationRegistryId.isNullOrBlank()) {
//                            logger.debug(
//                                "Presentation is requesting proof of non revocation for referent '${item.referent}', creating revocation state for credential",
//                                mapOf(
//                                    "nonRevoked" to nonRevoked,
//                                    "credentialRevocationId" to credentialRevocationId,
//                                    "revocationRegistryId" to revocationRegistryId,
//                                    "timestamp" to timestamp
//                                )
//                            )
//
//                            // Boas práticas (Aries RFC 0441)
//                            RevocationInterval.assertBestPracticeRevocationInterval(nonRevoked)
//
//                            val revocationRegistryDefinition = RevocationRegistryDefinition(agent.ledgerService.getRevocationRegistryDefinition(revocationRegistryId))
//                            //val registry = agent.anonCredsRegistryService.getRegistryForIdentifier(revocationRegistryId)
//
//                            val anonCredsRevocationRegistryDefinition=AnonCredsRevocationRegistryDefinition(
//                                issuerId = revocationRegistryDefinition.issuerId(),
//                                credDefId = revocationRegistryDefinition.,
//                                tag = TODO(),
//                                value = TODO()
//                            )
//
//                            if (revocationRegistries[revocationRegistryId] == null) {
//                                if (revocationRegistryDefinition == null) {
//                                    throw CredoError(
//                                        "Could not retrieve revocation registry definition for revocation registry $revocationRegistryId"
//                                    )
//                                }
//
//                                val tailsFilePath : String = revocationRegistryDefinition.tailsLocation()
//                                // const { tailsFilePath } = await tailsFileService.getTailsFile(agentContext, {
//                                //              revocationRegistryDefinition,
//                                //            })
//
//                                revocationRegistries[revocationRegistryId] = AnonCredsRevocationRegistryEntry(
//                                    definition = revocationRegistryDefinition,
//                                    tailsFilePath = tailsFilePath,
//                                    revocationStatusLists = mutableMapOf()
//                                )
//                            }
//
//                            val timestampToFetch = timestamp ?: nonRevoked.to
//                            if (timestampToFetch != null) {
//                                // Status list por timestamp
//                                val bucket = revocationRegistries.getValue(revocationRegistryId)
//                                if (!bucket.revocationStatusLists.containsKey(timestampToFetch)) {
//                                    val revocationStatusList = agent.ledgerService.getRevocationStatusList(revocationRegistryId, timestampToFetch.toInt())
//
//                                    if (revocationStatusList == null) {
//                                        throw CredoError(
//                                            "Could not retrieve revocation status list for revocation registry $revocationRegistryId"
//                                        )
//                                    }
//
//                                    bucket.revocationStatusLists[revocationStatusList.timestamp.toLong()] = revocationStatusList
//
//                                    // Se o selected não tinha timestamp, use o do status list
//                                    if (timestamp == null) {
//                                        timestampUpdates += TimestampUpdate(
//                                            type = item.type,
//                                            referent = item.referent,
//                                            timestamp = revocationStatusList.timestamp.toLong()
//                                        )
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//                .awaitAll()
//
//            // Aplicar os timestamps coletados sem mutar os mapas originais
//            if (timestampUpdates.isNotEmpty()) {
//                val newAttrs = selectedCredentials.attributes.toMutableMap()
//                val newPreds = selectedCredentials.predicates.toMutableMap()
//
//                for ((type, referent, ts) in timestampUpdates) {
//                    when (type) {
//                        ReferentItem.Type.ATTRIBUTES -> {
//                            newAttrs[referent]?.let { match ->
//                                newAttrs[referent] = match.copy(timestamp = ts)
//                            }
//                        }
//                        ReferentItem.Type.PREDICATES -> {
//                            newPreds[referent]?.let { match ->
//                                newPreds[referent] = match.copy(timestamp = ts)
//                            }
//                        }
//                    }
//                }
//                updatedSelectedCredentials = updatedSelectedCredentials.copy(
//                    attributes = newAttrs,
//                    predicates = newPreds
//                )
//            }
//
//            logger.debug(
//                "Retrieved revocation registries for proof request",
//                mapOf("revocationRegistries" to revocationRegistries)
//            )
//
//            RevocationRegistriesForRequestResult(
//                revocationRegistries = revocationRegistries,
//                updatedSelectedCredentials = updatedSelectedCredentials
//            )
//        } catch (e: Throwable) {
//            logger.error(
//                "Error retrieving revocation registry for proof request",
//                mapOf("error" to e, "proofRequest" to proofRequest, "selectedCredentials" to selectedCredentials)
//            )
//            throw e
//        }
//    }
//
//    suspend fun getRevocationRegistriesForProof(
//        proof: AnonCredsProof
//    ): Map<String, RevocationRegistryEntry> = coroutineScope {
//        val revocationRegistries = mutableMapOf<String, RevocationRegistryEntry>()
//
//        // Executa as buscas em paralelo, como Promise.all
//        val jobs = proof.identifiers.mapNotNull { identifier ->
//            val revocationRegistryId = identifier.revRegId
//            val timestamp = identifier.timestamp
//
//            // Pula se não houver revocationRegistryId ou timestamp
//            if (revocationRegistryId == null || timestamp == null) return@mapNotNull null
//
//            async {
////                val registry = agentContext.dependencyManager
////                    .resolve(AnonCredsRegistryService::class)
////                    .getRegistryForIdentifier(agentContext, revocationRegistryId)
//
//                // --- Definição do registry (se ainda não buscada)
//                val entry = synchronized(revocationRegistries) {
//                    revocationRegistries[revocationRegistryId]
//                } ?: run {
//
////                    val (revocationRegistryDefinition, resolutionMetadata) =
////                        agent.ledgerService.getRevocationRegistryDefinition(revocationRegistryId)
//                    val revocationRegistryDefinition = RevocationRegistryDefinition(agent.ledgerService.getRevocationRegistryDefinition(revocationRegistryId))
//                    val revocation = agent.ledgerService.getRevocationRegistryDefinition(revocationRegistryId)
//                    val revocationRegistryResult = Json.decodeFromString<FetchIntermediateRevocationRegistryDefinitionResult>(revocation)
//
//                    val definition = revocationRegistryDefinition
//                        ?: throw CredoError(
//                            "Could not retrieve revocation registry definition for revocation registry " +
//                                    "$revocationRegistryId"
//                        )
//
//                    val created = RevocationRegistryEntry(definition = definition)
//                    synchronized(revocationRegistries) {
//                        // evita condição de corrida se outra coroutine já inseriu
//                        revocationRegistries.putIfAbsent(revocationRegistryId, created) ?: created
//                    }
//                }
//
//                // --- Status list por timestamp (se ainda não buscada)
//                val hasStatusList = synchronized(entry.revocationStatusLists) {
//                    entry.revocationStatusLists.containsKey(timestamp)
//                }
//                if (!hasStatusList) {
//                    val (revocationStatusList, statusListResolutionMetadata) =
//                        agent.anonCredsRegistryService.getRevocationStatusList(revocationRegistryId, timestamp)
//
//                    val statusList = revocationStatusList
//                        ?: throw CredoError(
//                            "Could not retrieve revocation status list for revocation registry " +
//                                    "$revocationRegistryId: ${statusListResolutionMetadata.message}"
//                        )
//
//                    synchronized(entry.revocationStatusLists) {
//                        entry.revocationStatusLists.putIfAbsent(timestamp, statusList)
//                    }
//                }
//            }
//        }
//
//        jobs.awaitAll()
//        revocationRegistries
//    }

}