package org.hyperledger.ariesframework.anoncreds.formats.utils

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
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryEntry
import org.hyperledger.ariesframework.anoncreds.model.RevocationRegistriesForRequestResult
import org.hyperledger.ariesframework.anoncreds.model.RevocationRegistryBucket
import org.hyperledger.ariesframework.anoncreds.model.RevocationRegistryValue
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval
import org.hyperledger.ariesframework.anoncreds.service.tails.GetTailsFileOptions
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.proofs.v2.verifier.RevocationRegistryEntry
import org.hyperledger.ariesframework.util.LogUtil
import org.slf4j.LoggerFactory
import uniffi.indy_besu_vdr.RevocationRegistryDefinition
import uniffi.indy_besu_vdr.RevocationStatusList
import java.util.Collections

data class RevocationRegistries(val agent: Agent) {

    private val logger = LoggerFactory.getLogger(RevocationRegistries::class.java)

    suspend fun getRevocationRegistriesForRequest(
        proofRequest: AnonCredsProofRequest,
        selectedCredentials: AnonCredsSelectedCredentials,
    ): RevocationRegistriesForRequestResult {
        val updatedSelectedCredentials = selectedCredentials
        val revocationRegistries: MutableMap<String, RevocationRegistryBucket> = mutableMapOf()

        LogUtil.info(this) { "Retrieving revocation registries for proof request $proofRequest $selectedCredentials" }
        val referentCredentials = mutableListOf<Map<String, Any?>>()

        for ((referent, selectedCredential) in selectedCredentials.attributes) {
            referentCredentials.add(
                mapOf(
                    "type" to "attributes",
                    "referent" to referent,
                    "selectedCredential" to selectedCredential,
                    "nonRevoked" to (
                        proofRequest.requestedAttributes[referent]?.nonRevoked
                            ?: proofRequest.nonRevoked
                        ),
                ),
            )
        }

        for ((referent, selectedCredential) in selectedCredentials.predicates) {
            referentCredentials.add(
                mapOf(
                    "type" to "predicates",
                    "referent" to referent,
                    "selectedCredential" to selectedCredential,
                    "nonRevoked" to (
                        proofRequest.requestedPredicates[referent]?.nonRevoked
                            ?: proofRequest.nonRevoked
                        ),
                ),
            )
        }

        val revocationRegistryPromises = mutableListOf<Any>()

        for (credential in referentCredentials) {
            val referent = credential["referent"] as String

            val nonRevoked = credential["nonRevoked"] as? AnonCredsNonRevokedInterval
            val type = credential["type"] as String

            val selected = credential["selectedCredential"]
            val info = when (selected) {
                is AnonCredsRequestedPredicateMatch -> selected.credentialInfo
                is AnonCredsRequestedAttributeMatch -> selected.credentialInfo
                else -> throw CredoError(
                    "selectedCredential inválido para referent '$referent': ${selected?.javaClass?.name}",
                )
            }

            if (info == null) {
                throw CredoError(
                    "Credential for referent '$referent' does not have credentialInfo to create revocation state",
                )
            }

            val credentialRevocationId = info.credentialRevocationId
            val revocationRegistryId = info.revocationRegistryId
            val timestamp = when (selected) {
                is AnonCredsRequestedPredicateMatch -> selected.timestamp
                is AnonCredsRequestedAttributeMatch -> selected.timestamp
                else -> throw CredoError(
                    "timestamp inválido para referent '$referent': ${selected?.javaClass?.name}",
                )
            }

            if (nonRevoked != null && credentialRevocationId != null && revocationRegistryId != null) {
                LogUtil.info(this) {
                    "Presentation is requesting proof of non revocation for referent '$referent', creating revocation state for credential: nonRevoked=$nonRevoked, credentialRevocationId=$credentialRevocationId, revocationRegistryId=$revocationRegistryId, timestamp=$timestamp"
                }

                LogUtil.info(this) {
                    "REVREG_DECISION isWebVh=${isWebVh(revocationRegistryId)} revocationRegistryId=$revocationRegistryId method=${runCatching { agent.anonCredsRegistryService.getRegistryForIdentifier(revocationRegistryId).methodName }.getOrElse { "?" }}"
                }

                // descomentar depois? ver oq fazer
                // RevocationInterval.assertBestPracticeRevocationInterval(nonRevoked)

                if (isWebVh(revocationRegistryId)) {
                    val webvhEntry = fetchWebVhRevocationRegistryEntry(revocationRegistryId)
                    revocationRegistries.put(
                        key = revocationRegistryId,
                        value = RevocationRegistryBucket(
                            definition = null,
                            tailsFilePath = webvhEntry.tailsFilePath,
                            tailsHash = webvhEntry.tailsHash,
                            webvhEntry = webvhEntry,
                        ),
                    )
                } else {
                    val revocationRegistry: RevocationRegistryDefinition = agent.ledgerService.getRevocationRegistryDefinitionIndyBesuLib(revocationRegistryId)

                    if (revocationRegistry == null) {
                        throw Exception("Could not retrieve revocation registry definition for revocation registry $revocationRegistryId")
                    }

                    val revRegValue: RevocationRegistryValue =
                        Json.decodeFromString(revocationRegistry.value)

                    revocationRegistries.put(
                        key = revocationRegistryId,
                        value = RevocationRegistryBucket(
                            tailsFilePath = agent.ledgerService.getTailsPath(),
                            tailsHash = revRegValue.tailsHash,
                            definition = revocationRegistry,
                        ),
                    )
                }
            }

            val timestampToFetch: ULong? = timestamp ?: nonRevoked?.to

            LogUtil.info(this) {
                "referent '$referent',: revocationRegistryId=$revocationRegistryId, revocationRegistries=${revocationRegistries[revocationRegistryId]}, revocationRegistryId=$revocationRegistryId, timestamp=$timestamp"
            }

            val isWebVhBucket = revocationRegistryId != null &&
                revocationRegistries[revocationRegistryId]?.webvhEntry != null

            if (timestampToFetch != null && revocationRegistryId != null && isWebVhBucket) {
                val webvhEntry = revocationRegistries[revocationRegistryId]!!.webvhEntry!!
                if (webvhEntry.revocationStatusLists?.get(timestampToFetch) == null) {
                    val registry =
                        agent.anonCredsRegistryService.getRegistryForIdentifier(revocationRegistryId)
                    val (statusList, _) =
                        registry.getRevocationStatusList(agent, revocationRegistryId, timestampToFetch)
                            ?: throw CredoError(
                                "Could not retrieve webvh revocation status list for revocation registry " +
                                    "$revocationRegistryId",
                            )
                    val nonNullStatusList = statusList ?: throw CredoError(
                        "Could not retrieve webvh revocation status list for revocation registry " +
                            "$revocationRegistryId",
                    )
                    val statusMap = webvhEntry.revocationStatusLists?.toMutableMap() ?: mutableMapOf()
                    statusMap[timestampToFetch] = nonNullStatusList
                    revocationRegistries[revocationRegistryId] =
                        RevocationRegistryBucket(
                            definition = null,
                            tailsFilePath = webvhEntry.tailsFilePath,
                            tailsHash = webvhEntry.tailsHash,
                            webvhEntry = webvhEntry.copy(revocationStatusLists = statusMap),
                        )
                }
            } else if (timestampToFetch != null &&
                revocationRegistryId != null &&
                revocationRegistries.isNotEmpty() &&
                revocationRegistries[revocationRegistryId]?.revocationStatusLists?.get(timestampToFetch) == null
            ) {
                val revocationStatusList: RevocationStatusList =
                    agent.ledgerService
                        .getRevocationStatusList(
                            id = revocationRegistryId,
                            timestamp = timestampToFetch,
                        )

                if (revocationStatusList == null) {
                    throw CredoError(
                        "Could not retrieve revocation status list for revocation registry " +
                            "$revocationRegistryId",
                    )
                }

                val revocationStatusMap: MutableMap<ULong, RevocationStatusList> = mutableMapOf(
                    revocationStatusList.timestamp to revocationStatusList,
                )

                val revocationRegistryEntry = RevocationRegistryBucket(
                    definition = revocationRegistries.get(revocationRegistryId)!!.definition,
                    tailsFilePath = revocationRegistries.get(revocationRegistryId)!!.tailsFilePath,
                    tailsHash = revocationRegistries.get(revocationRegistryId)!!.tailsHash,
                    revocationStatusLists = revocationStatusMap,
                )

                revocationRegistries.put(
                    key = revocationRegistryId,
                    value = revocationRegistryEntry,
                )

//                if (timestamp == null) {
//                    val credsOfType = updatedSelectedCredentials[type]?.toMutableMap() ?: mutableMapOf()
//                    val referentEntry = credsOfType[referent]?.toMutableMap() ?: mutableMapOf()
//
//                    referentEntry["timestamp"] = revocationStatusList.timestamp
//                    credsOfType[referent] = referentEntry
//
//                    val updatedType = updatedSelectedCredentials.toMutableMap()
//                    updatedType[type] = credsOfType
//
//                    updatedSelectedCredentials = updatedType
            }
        }

        LogUtil.info(this) {
            "Retrieved revocation registries for proof request: $revocationRegistries"
        }

        return RevocationRegistriesForRequestResult(
            revocationRegistries = revocationRegistries,
            updatedSelectedCredentials = selectedCredentials,
        )
    }

    suspend fun getRevocationRegistriesForProof(
        proof: AnonCredsProof,
    ): Map<String, RevocationRegistryEntry> = coroutineScope {
        // Cache compartilhado entre coroutines
        val revocationRegistries: MutableMap<String, RevocationRegistryEntry> =
            Collections.synchronizedMap(mutableMapOf())

        // Dispara tarefas para cada identifier que tenha rev_reg_id e timestamp
        val jobs = proof.identifiers.mapNotNull { identifier ->
            val revocationRegistryId = identifier.revRegId
            val timestamp = identifier.timestamp

            if (revocationRegistryId.isNullOrBlank() || timestamp == null) {
                null // pula se faltar info
            } else {
                async {
                    val registry = agent.anonCredsRegistryService.getRegistryForIdentifier(revocationRegistryId)

                    // 1) Fetch definition if not yet in cache
                    if (revocationRegistries[revocationRegistryId] == null) {
                        val (revocationRegistryDefinition, resolutionMetadata) =
                            registry.getRevocationRegistryDefinition(agent, revocationRegistryId)

                        if (revocationRegistryDefinition == null) {
                            throw CredoError(
                                "Could not retrieve revocation registry definition for revocation registry " +
                                    "$revocationRegistryId",
                            )
                        }

                        revocationRegistries[revocationRegistryId] =
                            RevocationRegistryEntry(definition = revocationRegistryDefinition)
                    }

                    // 2) Fetch status list for timestamp if not yet available
                    val entry = revocationRegistries.getValue(revocationRegistryId)
                    if (entry.revocationStatusLists?.get(timestamp) == null) {
                        val (revocationStatusList, statusListResolutionMetadata) =
                            registry.getRevocationStatusList(agent, revocationRegistryId, timestamp.toULong())

                        if (revocationStatusList == null) {
                            throw CredoError(
                                "Could not retrieve revocation status list for revocation registry " +
                                    "$revocationRegistryId}",
                            )
                        }

                        entry.revocationStatusLists?.set(timestamp, revocationStatusList)
                    }
                }
            }
        }

        // Equivalente a Promise.all(...)
        jobs.awaitAll()

        revocationRegistries
    }

    private fun isWebVh(id: String): Boolean {
        return id.startsWith("did:webvh:") ||
            id.startsWith("did:web:")
    }

    private suspend fun fetchWebVhRevocationRegistryEntry(
        revocationRegistryId: String,
    ): AnonCredsRevocationRegistryEntry {
        val registry = agent.anonCredsRegistryService.getRegistryForIdentifier(revocationRegistryId)

        val result =
            registry.getRevocationRegistryDefinition(agent, revocationRegistryId)
                ?: throw CredoError(
                    "Could not retrieve webvh revocation registry definition for revocation registry $revocationRegistryId",
                )
        val revocationRegistryDefinition = result.revocationRegistryDefinition
            ?: throw CredoError(
                "Could not retrieve webvh revocation registry definition for revocation registry $revocationRegistryId",
            )

        val tailsBasePath = agent.anoncredsmodulesconfig.tailsFileService.getTailsBasePath()
        agent.anoncredsmodulesconfig.tailsFileService.getTailsFile(
            options = GetTailsFileOptions(
                revocationRegistryDefinition = revocationRegistryDefinition,
                revocationRegistryDefinitionId = null,
            ),
        )

        return AnonCredsRevocationRegistryEntry(
            tailsFilePath = tailsBasePath,
            tailsHash = revocationRegistryDefinition.value.tailsHash,
            definition = revocationRegistryDefinition,
            revocationStatusLists = mutableMapOf(),
        )
    }
}
