package org.hyperledger.ariesframework.proofs.repository.verifier

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class VerifierRepository(agent: Agent) :
    Repository<VerifierRecord>(VerifierRecord::class, agent) {

    /**
     * Retrieves a VerifierRecord by proofRecordId.
     */
    suspend fun getByProofRecordId(proofRecordId: String): VerifierRecord {
        val query = """{"proofRecordId": "$proofRecordId"}"""
        println("🕵️‍♂️ Query sent to getSingleByQuery: $query")

        return try {
            val record = getSingleByQuery(query)
            println("✅ Record found: $record")
            record
        } catch (e: Exception) {
            println("❌ Error in getByProofRecordId: ${e.message}")
            throw e
        }
    }

    /**
     * Retrieves a VerifierRecord by globalThreadId.
     */
    suspend fun getByGlobalThreadId(globalThreadId: String): VerifierRecord {
        val query = """{"globalThreadId": "$globalThreadId"}"""
        return getSingleByQuery(query)
    }
}
