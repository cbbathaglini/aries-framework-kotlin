package org.hyperledger.ariesframework.anoncreds.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class AnonCredsCredentialDefinitionPrivateRepository(agent: Agent) : Repository<AnonCredsCredentialDefinitionPrivateRecord>(
    AnonCredsCredentialDefinitionPrivateRecord::class,
    agent,
) {
    suspend fun getByCredentialDefinitionId(credentialDefinitionId: String): AnonCredsCredentialDefinitionPrivateRecord {
        return getSingleByQuery("{\"credentialDefinitionId\": \"$credentialDefinitionId\"}")
    }
}