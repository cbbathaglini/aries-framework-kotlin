package org.hyperledger.ariesframework.proofs.utils

import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.Schema
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.util.concurrentForEach

class RecoverFromLedger {

    companion object {
        suspend fun getSchemas(schemaIds: Set<String>, agent: Agent): Map<String, Schema> {
            val schemas = mutableMapOf<String, Schema>()
            val lock = Mutex()

            schemaIds.concurrentForEach { schemaId ->
                val (schema, _) = agent.ledgerService.getSchema(schemaId)
                lock.withLock {
                    schemas[schemaId] = Schema(schema)
                }
            }

            return schemas
        }

        suspend fun getCredentialDefinitions(credentialDefinitionIds: Set<String>, agent: Agent): Map<String, CredentialDefinition> {
            val credentialDefinitions = mutableMapOf<String, CredentialDefinition>()
            val lock = Mutex()

            credentialDefinitionIds.concurrentForEach { credentialDefinitionId ->
                val credentialDefinition =
                    agent.ledgerService.getCredentialDefinition(credentialDefinitionId)
                lock.withLock {
                    credentialDefinitions[credentialDefinitionId] =
                        CredentialDefinition(credentialDefinition)
                }
            }

            return credentialDefinitions
        }
    }
}
