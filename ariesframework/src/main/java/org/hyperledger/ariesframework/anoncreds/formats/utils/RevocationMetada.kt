package org.hyperledger.ariesframework.anoncreds.formats.utils

import anoncreds_uniffi.CredentialRevocationState
import uniffi.indy_besu_vdr.RevocationStatusList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.GetRevocationMetadataParams
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.RevocationMetadataResult
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.proofs.models.NonRevokedIntervalOverride
import anoncreds_uniffi.RevocationRegistryDefinition
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition

class RevocationMetada {
  //  companion object{
//        suspend fun getRevocationMetadata(
//            agent: Agent,
//            credentialRevocationMetadata: GetRevocationMetadataParams,
//            mustHaveTimeStamp: Boolean = false
//        ): RevocationMetadataResult = withContext(Dispatchers.IO) {
//            var nonRevokedIntervalOverride: NonRevokedIntervalOverride? = null
//
//            val revocationRegistryId = credentialRevocationMetadata.revocationRegistryId
//            val revocationRegistryIndex = credentialRevocationMetadata.revocationRegistryIndex
//            val nonRevokedInterval = credentialRevocationMetadata.nonRevokedInterval
//            val providedTimestamp = credentialRevocationMetadata.timestamp
//
//            if (revocationRegistryId.isNullOrBlank() ||
//                nonRevokedInterval == null ||
//                (mustHaveTimeStamp && providedTimestamp == null)
//            ) {
//                throw CredoError("Invalid revocation metadata")
//            }
//
//            // RFC 0441 best practices
//            RevocationInterval.assertBestPracticeRevocationInterval(nonRevokedInterval)
//
//            // Definição do Registry
//            val revocationRegistryDefinition = agent.ledgerService.getRevocationRegistryDefinitionIndyBesuLib(revocationRegistryId)
//
//            val tailsFile = agent.revocationService.downloadTails(revocationRegistryDefinition)
//
////            // Baixar tails
////            val tailsFileService = agentContext
////                .dependencyManager
////                .resolve(AnonCredsModuleConfig::class.java)
////                .tailsFileService
////
////            val tailsFilePath = tailsFileService.getTailsFile(
////                agentContext,
////                TailsFileService.TailsRequest(
////                    revocationRegistryDefinition = RevocationRegistryDefinition.fromJson(
////                        anonCredsRevocationRegistryDefinitionJson
////                    )
////                )
////            ).tailsFilePath
//
//            // Timestamp a buscar
//            val timestampToFetch = providedTimestamp ?: nonRevokedInterval.to
//            ?: throw CredoError("Timestamp to fetch is required")
//
//            // Status list no timestamp solicitado
//            val revocationStatusList : RevocationStatusList = agent.ledgerService.getRevocationStatusList(revocationRegistryId, timestampToFetch.toInt())
//
//            val updatedTimestamp = providedTimestamp ?: revocationStatusList.timestamp
//
//            val credentialRevocationState = CreateRevocationStateOptions(
//                revocationRegistryIndex = revocationRegistryIndex!!,
//                revocationRegistryDefinition = revocationRegistryDefinition,
//                tailsPath = tailsFile.path,
//                revocationStatusList = revocationStatusList
//            )
//            // Revocation State (opcional)
//            val revocationState =
//                revocationRegistryIndex?.let { idx ->
//                    CredentialRevocationState(
//
//                    )
//                }
//
//            // Tratamento do "from" > timestampToFetch
//            val requestedFrom = nonRevokedInterval.from
//            if (requestedFrom != null && requestedFrom > timestampToFetch) {
//                val overrideResp =
//                    fetchRevocationStatusList(agentContext, revocationRegistryId, requestedFrom)
//                val overrideJson = when (val anyVal = overrideResp.revocationStatusList) {
//                    is JsonObject -> anyVal
//                    is Map<*, *> -> anyVal as JsonObject
//                    else -> throw CredoError("Formato inválido de revocationStatusList(override)")
//                }
//                val overrideStatusList = RevocationStatusList.fromJson(overrideJson)
//
//                val vdrTimestamp = overrideStatusList.timestamp
//                if (vdrTimestamp == timestampToFetch) {
//                    nonRevokedIntervalOverride = NonRevokedIntervalOverride(
//                        overrideRevocationStatusListTimestamp = timestampToFetch,
//                        requestedFromTimestamp = requestedFrom,
//                        revocationRegistryDefinitionId = revocationRegistryId
//                    )
//                } else {
//                    throw CredoError(
//                        "VDR timestamp para $requestedFrom não corresponde ao fornecido nos proof identifiers. " +
//                                "Esperado: $updatedTimestamp e recebido $vdrTimestamp"
//                    )
//                }
//            }
//
//            return@withContext RevocationMetadataResult(
//                updatedTimestamp = updatedTimestamp,
//                revocationRegistryId = revocationRegistryId,
//                revocationRegistryDefinition = revocationRegistryDefinition,
//                revocationStatusList = revocationStatusList,
//                nonRevokedIntervalOverride = nonRevokedIntervalOverride,
//                revocationState = revocationState
//            )
//        }
//    }
}