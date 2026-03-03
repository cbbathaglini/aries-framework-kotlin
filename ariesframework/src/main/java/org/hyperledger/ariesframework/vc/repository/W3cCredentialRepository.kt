package org.hyperledger.ariesframework.vc.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class W3cCredentialRepository(agent: Agent) : Repository<W3cCredentialRecord>(
    W3cCredentialRecord::class,
    agent,
) {

    suspend fun findByCredentialSubjectId(subjectId: String): List<W3cCredentialRecord> {
        val sid = subjectId.trim()
        if (sid.isEmpty()) return emptyList()

        val byFixed = findByQuery("{\"subjectId\": \"$sid\"}")
        if (byFixed.isNotEmpty()) return byFixed

        val key = "subjectId:$sid"
        val byDynamic = findByQuery("{\"$key\": \"1\"}")
        if (byDynamic.isNotEmpty()) return byDynamic

        return getAll().filter { rec ->
            rec.credential.credentialSubject.any { it.id?.trim() == sid }
        }
    }

}