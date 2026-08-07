package org.hyperledger.ariesframework.proofs.utils

import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.Schema
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.utils.AnonCredsObjects
import org.hyperledger.ariesframework.util.concurrentForEach

class RecoverFromLedger {

    companion object {
        suspend fun getSchemas(schemaIds: Set<String>, agent: Agent): Map<String, Schema> {
            val schemas = mutableMapOf<String, Schema>()
            val lock = Mutex()
            val json = Json { encodeDefaults = true }

            schemaIds.concurrentForEach { schemaId ->
                val schemaResult = AnonCredsObjects.fetchSchema(agent, schemaId)
                val schemaJson = json.encodeToString(schemaResult.schema)
                lock.withLock {
                    schemas[schemaId] = Schema(schemaJson)
                }
            }

            return schemas
        }

        suspend fun getCredentialDefinitions(credentialDefinitionIds: Set<String>, agent: Agent): Map<String, CredentialDefinition> {
            val credentialDefinitions = mutableMapOf<String, CredentialDefinition>()
            val lock = Mutex()

            credentialDefinitionIds.concurrentForEach { credentialDefinitionId ->
                val credentialDefinition =
                    AnonCredsObjects.fetchCredentialDefinitionJson(agent, credentialDefinitionId)
                lock.withLock {
                    credentialDefinitions[credentialDefinitionId] =
                        CredentialDefinition(credentialDefinition)
                }
            }

            return credentialDefinitions
        }
    }
}
