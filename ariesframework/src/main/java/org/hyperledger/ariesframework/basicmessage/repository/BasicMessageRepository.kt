package org.hyperledger.ariesframework.basicmessage.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class BasicMessageRepository(agent: Agent) : Repository<BasicMessageRecord>(BasicMessageRecord::class, agent) {
    suspend fun findByConnectionRecordId(connectionRecordId: String?): List<BasicMessageRecord> {
        return findByQuery("{\"connectionRecordId\": \"$connectionRecordId\"}")
    }
}
