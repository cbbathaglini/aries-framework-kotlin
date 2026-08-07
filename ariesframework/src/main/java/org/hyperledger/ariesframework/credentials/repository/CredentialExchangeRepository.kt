package org.hyperledger.ariesframework.credentials.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.credentials.models.CredentialRole
import org.hyperledger.ariesframework.storage.Repository

class CredentialExchangeRepository(agent: Agent) : Repository<CredentialExchangeRecord>(
    CredentialExchangeRecord::class,
    agent,
) {

    suspend fun getByCredentialId(credentialId: String): CredentialExchangeRecord {
        return getSingleByQuery("{\"credentialId\": \"$credentialId\"}")
    }

    suspend fun getByW3cCredentialId(w3cId: String): CredentialExchangeRecord {
        val all = getAll()

        return all.firstOrNull { record ->
            record.credentials.any { it.credentialRecordId == w3cId }
        } ?: throw IllegalArgumentException("Credential not found for w3cCredentialId=$w3cId")
    }

    suspend fun findByThreadAndConnectionId(threadId: String, connectionId: String?): CredentialExchangeRecord? {
        return if (connectionId != null) {
            findSingleByQuery("{\"threadId\": \"$threadId\", \"connectionId\": \"$connectionId\"}")
        } else {
            findSingleByQuery("{\"threadId\": \"$threadId\"}")
        }
    }

    suspend fun getByThreadAndConnectionId(threadId: String, connectionId: String?): CredentialExchangeRecord {
        return if (connectionId != null) {
            getSingleByQuery("{\"threadId\": \"$threadId\", \"connectionId\": \"$connectionId\"}")
        } else {
            getSingleByQuery("{\"threadId\": \"$threadId\"}")
        }
    }

    suspend fun getByThreadAndRole(threadId: String, role: CredentialRole?): CredentialExchangeRecord? {
        return getSingleByQuery("{\"threadId\": \"$threadId\",\"role\": \"${role}\"}")
    }

    suspend fun getByThreadAndRoleAndConnectionId(threadId: String, role: String?, connectionId: String?): CredentialExchangeRecord {
        return getSingleByQuery("{\"threadId\": \"$threadId\", \"connectionId\": \"$connectionId\", \"role\": \"$role\"}")
    }

    suspend fun findByThreadRoleAndConnectionId(
        threadId: String,
        role: CredentialRole?,
        connectionId: String?,
    ): CredentialExchangeRecord? {
        val queryMap = mutableMapOf("threadId" to threadId)

        connectionId?.let { queryMap["connectionId"] = it }
        role?.let { queryMap["role"] = it.toString() }

        val query = queryMap.entries.joinToString(
            separator = ", ",
            prefix = "{",
            postfix = "}",
        ) { "\"${it.key}\": \"${it.value}\"" }

        return findSingleByQuery(query)
    }

    suspend fun getByConnectionId(connectionId: String): List<CredentialExchangeRecord> {
        return findByQuery("{\"connectionId\": \"$connectionId\"}")
    }

    suspend fun getCredentialRecordId(credentialRecordId: String?): CredentialExchangeRecord? {
        val allRecords: List<CredentialExchangeRecord> = getAll()
        return allRecords.find { record ->
            record.credentials.any { it.credentialRecordId == credentialRecordId }
        }
    }

    suspend fun getByRevocationRegistryId(revocationRegistryId: String, anoncredsType: Boolean): CredentialExchangeRecord {
        if (!anoncredsType) return getSingleByQuery("{\"revocationRegistryId\": \"$revocationRegistryId\"}")
        return getSingleByQuery("{\"anonCredsRevocationRegistryId\": \"$revocationRegistryId\"}")
    }

    suspend fun getByCredentialRevocationId(credentialRevocationId: String, anoncredsType: Boolean): CredentialExchangeRecord {
        if (!anoncredsType) return getSingleByQuery("{\"credentialRevocationId\": \"$credentialRevocationId\"}")
        return getSingleByQuery("{\"anonCredsCredentialRevocationId\": \"$credentialRevocationId\"}")
    }

    suspend fun getByCredentialRevocationIdAndRevocationRegistryId(credentialRevocationId: String, revocationRegistryId: String, anoncredsType: Boolean): CredentialExchangeRecord {
        if (!anoncredsType) {
            return getSingleByQuery("{\"credentialRevocationId\": \"$credentialRevocationId\", \"revocationRegistryId\": \"$revocationRegistryId\"}")
        }
        return getSingleByQuery("{\"anonCredsCredentialRevocationId\": \"$credentialRevocationId\", \"anonCredsRevocationRegistryId\": \"$revocationRegistryId\"}")
    }
}
