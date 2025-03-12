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

    suspend fun getByRevocationRegistryId(revocationRegistryId: String): CredentialRecord {
        return getSingleByQuery("{\"revocationRegistryId\": \"$revocationRegistryId\"}")
    }

    suspend fun getByCredentialRevocationId(credentialRevocationId: String): CredentialRecord {
        return getSingleByQuery("{\"credentialRevocationId\": \"$credentialRevocationId\"}")
    }

    suspend fun getByCredentialRevocationIdAndRevocationRegistryId(credentialRevocationId: String, revocationRegistryId:String): CredentialRecord {
        return getSingleByQuery("{\"credentialRevocationId\": \"$credentialRevocationId\", \"revocationRegistryId\": \"$revocationRegistryId\"}")
    }
}
