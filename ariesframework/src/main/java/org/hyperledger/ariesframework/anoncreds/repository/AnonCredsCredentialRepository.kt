package org.hyperledger.ariesframework.anoncreds.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class AnonCredsCredentialRepository(agent: Agent) : Repository<AnonCredsCredentialRecord>(
    AnonCredsCredentialRecord::class,
    agent,
) {
    suspend fun getByCredentialDefinitionId( credentialDefinitionId: String): AnonCredsCredentialRecord{
        return getSingleByQuery("{\"credentialDefinitionId\": \"$credentialDefinitionId\"}")
    }

    suspend fun findByCredentialDefinitionId(credentialDefinitionId: String): AnonCredsCredentialRecord?{
        return findSingleByQuery("{\"credentialDefinitionId\": \"$credentialDefinitionId\"}")
    }

    suspend fun getByCredentialId(credentialId: String): AnonCredsCredentialRecord{
        return getSingleByQuery("{\"credentialId\": \"$credentialId\"}")
    }

    suspend fun findByCredentialId(credentialId: String): AnonCredsCredentialRecord?{
        return findSingleByQuery("{\"credentialId\": \"$credentialId\"}")
    }

}