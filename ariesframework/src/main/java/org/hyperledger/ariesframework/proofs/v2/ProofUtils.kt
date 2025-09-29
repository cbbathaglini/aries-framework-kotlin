package org.hyperledger.ariesframework.proofs.v2

import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.Schema
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.proofs.messages.v2.RequestPresentationMessageV2
import org.hyperledger.ariesframework.proofs.models.ProofFormatSpec
import org.hyperledger.ariesframework.proofs.models.RetrievedCredentials
import org.hyperledger.ariesframework.proofs.models.RetrievedCredentialsAnonCreds
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.util.concurrentForEach
import org.slf4j.LoggerFactory

class ProofUtils {

    companion object {
        private val logger = LoggerFactory.getLogger(ProofUtils::class.java)

        suspend fun getSchemas(agent: Agent, schemaIds: Set<String>): Map<String, AnonCredsSchema> {
            val schemas = mutableMapOf<String, AnonCredsSchema>()
            val lock = Mutex()

            schemaIds.concurrentForEach { schemaId ->
                val anonCredsSchema = agent.ledgerService.getSchemaObj(schemaId)
                lock.withLock {
                    schemas[schemaId] = anonCredsSchema
                }
            }

            return schemas
        }

        suspend fun getSchemasUniffi(agent: Agent, schemaIds: Set<String>): Map<String, Schema> {
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

        suspend fun getCredentialDefinitionsUniffi(
            agent: Agent,
            credentialDefinitionIds: Set<String>,
        ): Map<String, CredentialDefinition> {
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

        suspend fun getCredentialDefinitions(
            agent: Agent,
            credentialDefinitionIds: Set<String>,
        ): Map<String, AnonCredsCredentialDefinition> {
            val credentialDefinitions = mutableMapOf<String, AnonCredsCredentialDefinition>()
            val lock = Mutex()

            credentialDefinitionIds.concurrentForEach { credentialDefinitionId ->
                val cd =
                    agent.ledgerService.getCredentialDefinition(credentialDefinitionId)
                val credentialDefinition = cd.replace("\\\"", "\"")
                val anoncreds =
                    Json.decodeFromString<AnonCredsCredentialDefinition>(credentialDefinition)
                lock.withLock {
                    credentialDefinitions[credentialDefinitionId] = anoncreds
                }
            }

            return credentialDefinitions
        }

        /**
         * Create a [RetrievedCredentials] object. Given input proof request,
         * use credentials in the wallet to build indy requested credentials object for proof creation.
         *
         * @param proofRecordId the id of the proof request to get the matching credentials for.
         * @return [RetrievedCredentials] object.
         */
        suspend fun getRequestedCredentialsForProofRequest(proofRecordId: String, agent: Agent): RetrievedCredentialsAnonCreds {
            val record = agent.proofRepository.getById(proofRecordId)

            checkIfMessageTypeIsCorrect(proofRecordId, agent)

            val proofRequestMessageJson = agent.didCommMessageRepository.getAgentMessage(
                record.id,
                RequestPresentationMessageV2.type,
            )

            val proofRequestMessage =
                MessageSerializer.decodeFromString(proofRequestMessageJson) as RequestPresentationMessageV2
            updateProofFormat(record, proofRequestMessage.formats, agent)

            val proofRequestJson = proofRequestMessage.anoncredsProofRequest()
            logger.debug("Proof request json: $proofRequestJson")
            val proofRequest = Json.decodeFromString<AnonCredsProofRequest>(proofRequestJson)

            return agent.proofServiceV2.getRequestedCredentialsForProofRequest(proofRequest)
        }

        private suspend fun checkIfMessageTypeIsCorrect(proofRecordId: String, agent: Agent) {
            val recordMessageType =
                agent.didCommMessageRepository.getSingleByQuery("{\"associatedRecordId\": \"$proofRecordId\"}")

            if (!recordMessageType.message.contains("/2.0/")) {
                throw Exception("Version of proof protocol is incorrect")
            }
        }

        private suspend fun updateProofFormat(
            record: ProofExchangeRecord,
            formats: List<ProofFormatSpec>,
            agent: Agent
        ) {
            record.formats = formats
            agent.proofRepository.update(record)
        }
    }
}
