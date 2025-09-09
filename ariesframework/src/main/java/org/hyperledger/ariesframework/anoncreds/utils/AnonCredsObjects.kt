package org.hyperledger.ariesframework.anoncreds.utils

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.formats.AnoncredsCredentialFormatService
import org.hyperledger.ariesframework.anoncreds.model.FetchRevocationRegistryDefinitionResult
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList
import org.hyperledger.ariesframework.anoncreds.model.CredentialDefinitionResult
import org.hyperledger.ariesframework.error.CredoError
import org.slf4j.LoggerFactory

class AnonCredsObjects {

    companion object {
        private val logger = LoggerFactory.getLogger(AnonCredsObjects::class.java)

        suspend fun fetchCredentialDefinition(
            agent: Agent,
            credentialDefinitionId: String
        ): CredentialDefinitionResult {

            val registry =
                agent.anonCredsRegistryService.getRegistryForIdentifier(credentialDefinitionId)
            val result = registry.getCredentialDefinition(agent, credentialDefinitionId)

            val credentialDefinition = result.credentialDefinition
            val metadata = result.credentialDefinitionMetadata
            val resolutionMetadata = result.resolutionMetadata

            if (credentialDefinition == null) {
                throw CredoError(
                    "Credential definition not found for id $credentialDefinitionId: ${resolutionMetadata?.message}"
                )
            }

            val indyNamespace = metadata["didIndyNamespace"]
            val namespace = if (indyNamespace is String) indyNamespace else null

            return CredentialDefinitionResult(
                credentialDefinition = credentialDefinition,
                credentialDefinitionId = credentialDefinitionId,
                indyNamespace = namespace
            )
        }

        suspend fun fetchRevocationStatusList(
            agent: Agent,
            revocationRegistryId: String,
            timestamp: Long
        ): AnonCredsRevocationStatusList {

            val registry =
                agent.anonCredsRegistryService.getRegistryForIdentifier(revocationRegistryId)

            val result = registry.getRevocationStatusList(agent, revocationRegistryId, timestamp)
            val resolutionMetadata = result.resolutionMetadata

            return result.revocationStatusList ?: throw CredoError(
                "Could not retrieve revocation status list for revocation registry $revocationRegistryId: ${resolutionMetadata?.message}"
            )
        }

        suspend fun fetchRevocationRegistryDefinition(
            agent: Agent,
            revocationRegistryDefinitionId: String
        ): FetchRevocationRegistryDefinitionResult {

            val result = agent.anonCredsRegistryService
                .getRegistryForIdentifier(revocationRegistryDefinitionId)
                .getRevocationRegistryDefinition(revocationRegistryDefinitionId)

            if (result.revocationRegistryDefinition == null) {
                val message = result.resolutionMetadata?.message ?: "Unknown error"
                throw CredoError("RevocationRegistryDefinition not found for id $revocationRegistryDefinitionId: $message")
            }

            val indyNamespace =
                result.revocationRegistryDefinitionMetadata["didIndyNamespace"] as? String

            return FetchRevocationRegistryDefinitionResult(
                revocationRegistryDefinition = result.revocationRegistryDefinition,
                revocationRegistryDefinitionId = revocationRegistryDefinitionId,
                indyNamespace = indyNamespace
            )
        }
    }
}