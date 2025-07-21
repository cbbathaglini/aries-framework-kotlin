package org.hyperledger.ariesframework.anoncreds.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class AnonCredsCredentialDefinitionRepository(agent: Agent) : Repository<AnonCredsCredentialDefinitionRecord>(
    AnonCredsCredentialDefinitionRecord::class,
    agent,
) {

    suspend fun getByCredentialDefinitionId(credentialDefinitionId: String): AnonCredsCredentialDefinitionRecord {
        return getSingleByQuery("{\"credentialDefinitionId\": \"$credentialDefinitionId\"}")
    }

}