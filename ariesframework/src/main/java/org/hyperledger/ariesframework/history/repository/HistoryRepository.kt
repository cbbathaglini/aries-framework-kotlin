package org.hyperledger.ariesframework.history.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class HistoryRepository(agent: Agent) : Repository<HistoryRecord>(HistoryRecord::class, agent) {
    suspend fun findByConnectionId(connectionId: String?): List<HistoryRecord> {
        return findByQuery("{\"connectionId\": \"$connectionId\"}")
    }

    suspend fun findByAssociatedRecordId(associatedRecordId: String): List<HistoryRecord> {
        return findByQuery("{\"associatedRecordId\": \"$associatedRecordId\"}")
    }
}