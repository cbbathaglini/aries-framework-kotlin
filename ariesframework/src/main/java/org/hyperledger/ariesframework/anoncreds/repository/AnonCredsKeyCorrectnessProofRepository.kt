package org.hyperledger.ariesframework.anoncreds.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class AnonCredsKeyCorrectnessProofRepository(agent: Agent) : Repository<AnonCredsKeyCorrectnessProofRecord>(
    AnonCredsKeyCorrectnessProofRecord::class,
    agent,
) {

    suspend fun getByCredentialDefinitionId(credentialDefinitionId: String): AnonCredsKeyCorrectnessProofRecord {
        return getSingleByQuery("{\"credentialDefinitionId\": \"$credentialDefinitionId\"}")
    }

    suspend fun findByCredentialDefinitionId(credentialDefinitionId: String): AnonCredsKeyCorrectnessProofRecord? {
        return findSingleByQuery("{\"credentialDefinitionId\": \"$credentialDefinitionId\"}")
    }
}
