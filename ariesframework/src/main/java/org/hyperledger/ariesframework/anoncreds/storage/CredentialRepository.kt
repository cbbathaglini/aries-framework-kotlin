package org.hyperledger.ariesframework.anoncreds.storage

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class CredentialRepository(agent: Agent) : Repository<CredentialRecord>(
    CredentialRecord::class,
    agent,
) {
    suspend fun getByCredentialId(credentialId: String): CredentialRecord {
        return getSingleByQuery("{\"credentialId\": \"$credentialId\"}")
    }

    suspend fun getByConnectionId(connectionId: String) : List<CredentialRecord> {
        return findByQuery("{\"connectionId\": \"$connectionId\"}")
    }
}
