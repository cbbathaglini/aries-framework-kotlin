package org.hyperledger.ariesframework.anoncreds.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class AnonCredsRevocationRegistryDefinitionRepository(agent: Agent) : Repository<AnonCredsRevocationRegistryDefinitionRecord>(
    AnonCredsRevocationRegistryDefinitionRecord::class,
    agent,
) {

    suspend fun getByRevocationRegistryDefinitionId(revocationRegistryDefinitionId: String): AnonCredsRevocationRegistryDefinitionRecord {
        return getSingleByQuery("{\"revocationRegistryDefinitionId\": \"$revocationRegistryDefinitionId\"}")
    }

    suspend fun findByRevocationRegistryDefinitionId(revocationRegistryDefinitionId: String): AnonCredsRevocationRegistryDefinitionRecord? {
        return findSingleByQuery("{\"revocationRegistryDefinitionId\": \"$revocationRegistryDefinitionId\"}")
    }

    suspend fun findAllByCredentialDefinitionId(credentialDefinitionId: String): List<AnonCredsRevocationRegistryDefinitionRecord> {
        return findByQuery("{\"credentialDefinitionId\": \"$credentialDefinitionId\"}")
    }
}
