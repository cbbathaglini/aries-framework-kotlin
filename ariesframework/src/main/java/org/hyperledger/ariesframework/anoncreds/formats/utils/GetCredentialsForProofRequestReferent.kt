package org.hyperledger.ariesframework.anoncreds.formats.utils

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsCredentialsForProofRequest
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsGetCredentialsForProofRequestOptions
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsRequestedAttributeMatch
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsRequestedPredicateMatch
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.GetCredentialsForProofRequestOptions
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialInfo
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedAttribute
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedPredicate
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval
import org.hyperledger.ariesframework.anoncreds.model.holder.GetCredentialsForProofRequestReturn
import org.hyperledger.ariesframework.anoncreds.utils.AnonCredsObjects
import org.hyperledger.ariesframework.util.LogUtil
import org.slf4j.LoggerFactory
import java.util.Date

interface CredentialWithInfo {
    val credentialInfo: AnonCredsCredentialInfo
}

class GetCredentialsForProofRequestReferent {

    companion object {

        private val logger =
            LoggerFactory.getLogger(GetCredentialsForProofRequestReferent::class.java)

        suspend fun getCredentialsForProofRequestReferent(
            agent: Agent,
            proofRequest: AnonCredsProofRequest,
            attributeReferent: String,
            chosenCredentialId: String? = null,
        ): GetCredentialsForProofRequestReturn {
            return agent.anonCredsHolderService.getCredentialsForProofRequest(
                options = GetCredentialsForProofRequestOptions(
                    proofRequest = proofRequest,
                    attributeReferent = attributeReferent,
                    chosenCredentialId = chosenCredentialId,
                ),
            )
        }

        /**
         * Resultado da checagem de revogação.
         */
        data class RevocationStatusResult(
            val isRevoked: Boolean?, // null/undefined quando não aplicável
            val timestamp: ULong?, // epoch time conforme seu formato
        )

        /**
         * Equivalente ao getRevocationStatus do TS.
         */
        suspend fun getRevocationStatus(
            agent: Agent,
            proofRequest: AnonCredsProofRequest,
            requestedItem: Any, // AnonCredsRequestedAttribute | AnonCredsRequestedPredicate
            credentialInfo: AnonCredsCredentialInfo,
        ): RevocationStatusResult {
            val requestNonRevoked: AnonCredsNonRevokedInterval? = when (requestedItem) {
                is AnonCredsRequestedAttribute ->
                    requestedItem.nonRevoked
                        ?: proofRequest.nonRevoked

                is AnonCredsRequestedPredicate ->
                    requestedItem.nonRevoked
                        ?: proofRequest.nonRevoked

                else -> proofRequest.nonRevoked
            }

            val credentialRevocationId = credentialInfo.credentialRevocationId
            val revocationRegistryId = credentialInfo.revocationRegistryId

            if (requestNonRevoked == null || credentialRevocationId == null || revocationRegistryId.isNullOrBlank()) {
                return RevocationStatusResult(isRevoked = null, timestamp = null)
            }

            LogUtil.info(this) {
                "Fetching credential revocation status for credential revocation id '$credentialRevocationId' with revocation interval from '${requestNonRevoked.from}' to '${requestNonRevoked.to}'"
            }

            // Boas práticas (Aries RFC 0441) <<< DESCOMENTAR?
            // RevocationInterval.assertBestPracticeRevocationInterval(requestNonRevoked)

            val toTs = requestNonRevoked.to ?: dateToTimestamp(Date()).toULong()
            val revocationStatusList: AnonCredsRevocationStatusList =
                AnonCredsObjects.fetchRevocationStatusList(
                    agent = agent,
                    revocationRegistryId = revocationRegistryId,
                    timestamp = toTs,
                )

            val index = credentialRevocationId.toInt()
            val isRevoked = revocationStatusList.revocationList[index] == 1

            LogUtil.info(this) {
                "Credential with credential revocation index '$credentialRevocationId' is " + (if (isRevoked) "" else "not ") + "revoked with revocation interval to '${requestNonRevoked.to}' & from '${requestNonRevoked.from}'"
            }

            return RevocationStatusResult(
                isRevoked = isRevoked,
                timestamp = revocationStatusList.timestamp,
            )
        }

        suspend fun getCredentialsForAnonCredsProofRequest(
            agent: Agent,
            proofRequest: AnonCredsProofRequest,
            options: AnonCredsGetCredentialsForProofRequestOptions,
            chosenCredentialId: String? = null,
        ): AnonCredsCredentialsForProofRequest = coroutineScope {
            val attributesMap = mutableMapOf<String, List<AnonCredsRequestedAttributeMatch>>()
            val predicatesMap = mutableMapOf<String, List<AnonCredsRequestedPredicateMatch>>()

            // requested_attributes
            for ((referent, requestedAttribute) in proofRequest.requestedAttributes) {
                val getCredentialsForProofRequestReferentReturn =
                    getCredentialsForProofRequestReferent(
                        agent = agent,
                        proofRequest = proofRequest,
                        chosenCredentialId = chosenCredentialId,
                        attributeReferent = referent,
                    )

                val matches = getCredentialsForProofRequestReferentReturn.credentials
                    .map { credential ->
                        async {
                            val rev = getRevocationStatus(
                                agent = agent,
                                proofRequest = proofRequest,
                                requestedItem = requestedAttribute,
                                credentialInfo = credential.credentialInfo,
                            )
                            AnonCredsRequestedAttributeMatch(
                                credentialId = credential.credentialInfo.credentialId,
                                revealed = true,
                                credentialInfo = credential.credentialInfo,
                                timestamp = rev.timestamp,
                                revoked = rev.isRevoked,
                            )
                        }
                    }
                    .awaitAll()
                    .let { SortRequestedCredentialsMatches.sortRequestedCredentialsAttrMatches(it) }

                val filtered = if (options.filterByNonRevocationRequirements == true) {
                    matches.filter { it.revoked != true }
                } else {
                    matches
                }

                attributesMap[referent] = filtered
            }

            // requested_predicates
            for ((referent, requestedPredicate) in proofRequest.requestedPredicates) {
                val getCredentialsForProofRequestReferentReturn =
                    getCredentialsForProofRequestReferent(
                        agent = agent,
                        proofRequest = proofRequest,
                        chosenCredentialId = chosenCredentialId,
                        attributeReferent = referent,
                    )

                val matches = getCredentialsForProofRequestReferentReturn.credentials
                    .map { credential ->
                        async {
                            val rev = getRevocationStatus(
                                agent = agent,
                                proofRequest = proofRequest,
                                requestedItem = requestedPredicate,
                                credentialInfo = credential.credentialInfo,
                            )
                            AnonCredsRequestedPredicateMatch(
                                credentialId = credential.credentialInfo.credentialId,
                                credentialInfo = credential.credentialInfo,
                                timestamp = rev.timestamp,
                                revoked = rev.isRevoked,
                            )
                        }
                    }
                    .awaitAll()
                    .let { SortRequestedCredentialsMatches.sortRequestedCredentialsPredicatesMatches(it) }

                val filtered = if (options.filterByNonRevocationRequirements == true) {
                    matches.filter { it.revoked != true }
                } else {
                    matches
                }

                predicatesMap[referent] = filtered
            }

            AnonCredsCredentialsForProofRequest(
                attributes = attributesMap,
                predicates = predicatesMap,
            )
        }

        private fun dateToTimestamp(date: Date): Long {
            return date.time / 1000
        }
    }
}
