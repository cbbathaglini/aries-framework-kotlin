package org.hyperledger.ariesframework.anoncreds.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class AnonCredsRevocationRegistryDefinitionPrivateRepository (agent: Agent) : Repository<AnonCredsRevocationRegistryDefinitionPrivateRecord>(
    AnonCredsRevocationRegistryDefinitionPrivateRecord::class,
    agent,
) {

    suspend fun getByRevocationRegistryDefinitionId(revocationRegistryDefinitionId: String) :AnonCredsRevocationRegistryDefinitionPrivateRecord {
        return getSingleByQuery(revocationRegistryDefinitionId)
    }

    suspend fun findByRevocationRegistryDefinitionId(revocationRegistryDefinitionId: String) : AnonCredsRevocationRegistryDefinitionPrivateRecord?{
        return findSingleByQuery(revocationRegistryDefinitionId)
    }

    suspend fun findAllByCredentialDefinitionIdAndState(credentialDefinitionId: String, state: AnonCredsRevocationRegistryState?): List<AnonCredsRevocationRegistryDefinitionPrivateRecord> {
        return this.findByQuery("{\"credentialDefinitionId\": \"$credentialDefinitionId\", \"state\": \"${state?.name}\"}")
    }
}