package org.hyperledger.ariesframework.proofs.repository.verifier

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository


class VerifierRepository(agent: Agent) :
    Repository<VerifierRecord>(VerifierRecord::class, agent) {

    /**
     * Recupera um VerifierRecord pelo proofRecordId.
     */
    suspend fun getByProofRecordId(proofRecordId: String): VerifierRecord {
        val query = """{"proofRecordId": "$proofRecordId"}"""
        println("🕵️‍♂️ Query enviada para getSingleByQuery: $query")

        return try {
            val record = getSingleByQuery(query)
            println("✅ Registro encontrado: $record")
            record
        } catch (e: Exception) {
            println("❌ Erro em getByProofRecordId: ${e.message}")
            throw e
        }
    }

    /**
     * Recupera um VerifierRecord pelo globalThreadId.
     */
    suspend fun getByGlobalThreadId(globalThreadId: String): VerifierRecord {
        val query = """{"globalThreadId": "$globalThreadId"}"""
        return getSingleByQuery(query)
    }
}