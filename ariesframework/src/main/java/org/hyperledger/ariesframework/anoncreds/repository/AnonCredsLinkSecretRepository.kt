package org.hyperledger.ariesframework.anoncreds.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class AnonCredsLinkSecretRepository(agent: Agent) : Repository<AnonCredsLinkSecretRecord>(
    AnonCredsLinkSecretRecord::class,
    agent,
) {
    suspend fun getByLinkSecretId( linkSecretId: String): AnonCredsLinkSecretRecord{
        return getSingleByQuery("{\"linkSecretId\": \"$linkSecretId\"}")
    }

    suspend fun findByLinkSecretId(linkSecretId: String): AnonCredsLinkSecretRecord?{
        return findSingleByQuery("{\"linkSecretId\": \"$linkSecretId\"}")
    }

    suspend fun  findDefault() : AnonCredsLinkSecretRecord?{
        return findSingleByQuery("{\"isDefault\": \"true\"}")
    }
}