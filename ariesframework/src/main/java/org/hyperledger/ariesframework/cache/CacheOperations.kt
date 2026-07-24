package org.hyperledger.ariesframework.cache

import android.util.Log
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.utils.AnonCredsObjects
import org.slf4j.LoggerFactory

class CacheOperations {

    private val logger = LoggerFactory.getLogger(CacheOperations::class.java)

    suspend fun updateCache(agent: Agent) {
        logger.info("updating cache")
        val all = agent.credentialExchangeRepository.getAll().toList()

        val schemaIds = all.mapNotNull { it.schemaId }.toSet()
        val credDefIds = all.mapNotNull { it.credentialDefinitionId }.toSet()
        val revRegIds = all.mapNotNull { it.revRegDefId }.toSet()

        logger.info("-> schemaIds: $schemaIds |  credDefIds: $credDefIds |  revRegIds: $revRegIds ")

        for (schemaId in schemaIds) {
            runCatching { AnonCredsObjects.fetchSchema(agent, schemaId) }
                .onFailure { e -> Log.w("Cache", "Failed getSchema($schemaId): ${e.message}", e) }
        }

        for (credDefId in credDefIds) {
            runCatching { AnonCredsObjects.fetchCredentialDefinitionJson(agent, credDefId) }
                .onFailure { e -> Log.w("Cache", "Failed getCredentialDefinition($credDefId): ${e.message}", e) }
        }
        for (revRegId in revRegIds) {
            runCatching { agent.ledgerService.getRevocationRegistryDefinitionIndyBesuLib(revRegId) }
                .onFailure { e -> Log.w("Cache", "Failed getRevocationRegistryDefinition($revRegId): ${e.message}", e) }
        }

        runCatching { agent.ledgerService.getTailsPath() }
            .onFailure { e -> Log.w("Cache", "Failed getTailsPath(): ${e.message}", e) }
    }
}
