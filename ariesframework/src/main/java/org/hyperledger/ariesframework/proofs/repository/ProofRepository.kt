package org.hyperledger.ariesframework.proofs.repository

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.proofs.models.ProofRole
import org.hyperledger.ariesframework.storage.Repository

class ProofRepository(agent: Agent) :
    Repository<ProofExchangeRecord>(ProofExchangeRecord::class, agent) {
    suspend fun getByThreadAndConnectionId(
        threadId: String,
        connectionId: String?
    ): ProofExchangeRecord {
        return if (connectionId != null) {
            getSingleByQuery("{\"threadId\": \"$threadId\", \"connectionId\": \"$connectionId\"}")
        } else {
            getSingleByQuery("{\"threadId\": \"$threadId\"}")
        }
    }

    suspend fun getByConnectionId(connectionId: String?): ProofExchangeRecord {
        return getSingleByQuery("{ \"connectionId\": \"$connectionId\"}")
    }

    suspend fun findByThreadRoleAndConnection(
        threadId: String? = null,
        role: ProofRole? = null,
        connectionId: String? = null
    ): ProofExchangeRecord? {
        val queryObj = buildJsonObject {
            threadId?.takeIf { it.isNotBlank() }?.let { put("threadId", JsonPrimitive(it)) }
            connectionId?.takeIf { it.isNotBlank() }?.let { put("connectionId", JsonPrimitive(it)) }
            role?.name?.let { put("role", JsonPrimitive(it)) }
        }

        if (queryObj.isEmpty()) return null
        return getSingleByQuery(queryObj.toString())
    }

    suspend fun getByThreadAndConnectionIdAndRole(
        threadId: String?,
        connectionId: String?,
        role: String
    ): ProofExchangeRecord?{
        return findSingleByQuery("{\"threadId\": \"$threadId\", \"connectionId\": \"$connectionId\", \"role\": \"$role\"}")

    }

}
