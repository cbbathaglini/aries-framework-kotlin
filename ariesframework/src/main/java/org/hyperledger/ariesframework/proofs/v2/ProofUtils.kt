package org.hyperledger.ariesframework.proofs.v2

import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.Schema
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequestRestriction
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.anoncreds.utils.AnonCredsObjects
import org.hyperledger.ariesframework.proofs.models.PredicateType
import org.hyperledger.ariesframework.proofs.models.ProofFormatSpec
import org.hyperledger.ariesframework.proofs.models.RetrievedCredentials
import org.hyperledger.ariesframework.proofs.models.RetrievedCredentialsAnonCreds
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.proofs.v2.messages.RequestPresentationMessageV2
import org.hyperledger.ariesframework.util.PrintLongLine
import org.hyperledger.ariesframework.util.concurrentForEach
import kotlin.collections.component1
import kotlin.collections.component2

class ProofUtils {

    companion object {

        suspend fun getSchemas(agent: Agent, schemaIds: Set<String>): Map<String, AnonCredsSchema> {
            val schemas = mutableMapOf<String, AnonCredsSchema>()
            val lock = Mutex()

            schemaIds.concurrentForEach { schemaId ->
                val result = AnonCredsObjects.fetchSchema(agent, schemaId)
                lock.withLock {
                    schemas[schemaId] = result.schema!!
                }
            }

            return schemas
        }

        suspend fun getSchemasUniffi(agent: Agent, schemaIds: Set<String>): Map<String, Schema> {
            val schemas = mutableMapOf<String, Schema>()
            val lock = Mutex()

            schemaIds.concurrentForEach { schemaId ->
                val schemaResult = AnonCredsObjects.fetchSchema(agent, schemaId)
                val schemaJson = Json { encodeDefaults = true }.encodeToString(schemaResult.schema)
                lock.withLock {
                    schemas[schemaId] = Schema(schemaJson)
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
                    AnonCredsObjects.fetchCredentialDefinitionJson(agent, credentialDefinitionId)
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
                    AnonCredsObjects.fetchCredentialDefinitionJson(agent, credentialDefinitionId)
                PrintLongLine.print("cd: $cd")
                val credentialDefinition = cd.replace("\\\"", "\"")
//                PrintLongLine.print("cd: ${cd.toString()}")
                val anoncreds =
                    Json.decodeFromString<AnonCredsCredentialDefinition>(credentialDefinition)
                PrintLongLine.print("anoncreds: $anoncreds")
                lock.withLock {
                    credentialDefinitions[credentialDefinitionId] = anoncreds
                }
            }
            PrintLongLine.print("credentialDefinitions utils: $credentialDefinitions")
            return credentialDefinitions
        }

        /**
         * Create a [RetrievedCredentials] object. Given input proof request,
         * use credentials in the wallet to build indy requested credentials object for proof creation.
         *
         * @param proofRecordId the id of the proof request to get the matching credentials for.
         * @return [RetrievedCredentials] object.
         */
        suspend fun getRequestedCredentialsForProofRequest(proofRecordId: String, agent: Agent, credentialW3cId: String? = null): RetrievedCredentialsAnonCreds {
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
            val proofRequest = Json.decodeFromString<AnonCredsProofRequest>(proofRequestJson)

            return agent.proofServiceV2.getRequestedCredentialsForProofRequest(proofRequest, credentialW3cId = credentialW3cId)
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
            agent: Agent,
        ) {
            record.formats = formats
            agent.proofRepository.update(record)
        }

        fun getProofFormats(proofRequest: AnonCredsProofRequest, format: String): Map<String, JsonElement> {
            val (name, version, nonce, requestedAttributes, requestedPredicates, nonRevoked, ver) = proofRequest
            val returnVal = mapOf(
                format to buildJsonObject {
                    put("name", JsonPrimitive(name))
                    put("version", JsonPrimitive(version))

                    proofRequest.nonRevoked?.let { nr ->
                        put(
                            "non_revoked",
                            buildJsonObject {
                                nr.from?.let { put("from", JsonPrimitive(it)) }
                                nr.to?.let { put("to", JsonPrimitive(it)) }
                            },
                        )
                    }

                    put(
                        "requested_attributes",
                        buildJsonObject {
                            requestedAttributes.forEach { (key, attr) ->
                                put(
                                    key,
                                    buildJsonObject {
                                        attr.name?.let { put("name", JsonPrimitive(it)) }
                                        attr.names?.let { put("names", buildJsonArray { it.forEach { n -> add(JsonPrimitive(n)) } }) }
                                        attr.restrictions?.takeIf { it.isNotEmpty() }?.let { restrictions ->
                                            put(
                                                "restrictions",
                                                buildJsonArray {
                                                    restrictions.forEach { add(restrictionToJson(it)) }
                                                },
                                            )
                                        }
                                        attr.nonRevoked?.let {
                                            put(
                                                "non_revoked",
                                                buildJsonObject {
                                                    it.from?.let { f -> put("from", JsonPrimitive(f)) }
                                                    it.to?.let { t -> put("to", JsonPrimitive(t)) }
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        },
                    )

                    // requested_predicates
                    put(
                        "requested_predicates",
                        buildJsonObject {
                            requestedPredicates.forEach { (key, pred) ->
                                put(
                                    key,
                                    buildJsonObject {
                                        put("name", JsonPrimitive(pred.name))
                                        put("p_type", JsonPrimitive(pred.pType.toSymbol()))
                                        put("p_value", JsonPrimitive(pred.pValue))
                                        pred.restrictions?.takeIf { it.isNotEmpty() }?.let { restrictions ->
                                            put(
                                                "restrictions",
                                                buildJsonArray {
                                                    restrictions.forEach { add(restrictionToJson(it)) }
                                                },
                                            )
                                        }
                                        pred.nonRevoked?.let {
                                            put(
                                                "non_revoked",
                                                buildJsonObject {
                                                    it.from?.let { f -> put("from", JsonPrimitive(f)) }
                                                    it.to?.let { t -> put("to", JsonPrimitive(t)) }
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        },
                    )
                },
            )
            return returnVal
        }

        private fun PredicateType.toSymbol(): String {
            return when (this) {
                PredicateType.LessThan -> "<"
                PredicateType.LessThanOrEqualTo -> "<="
                PredicateType.GreaterThan -> ">"
                PredicateType.GreaterThanOrEqualTo -> ">="
            }
        }

        private fun restrictionToJson(r: AnonCredsProofRequestRestriction): JsonObject =
            buildJsonObject {
                r.schemaId?.let { put("schema_id", JsonPrimitive(it)) }
                r.schemaIssuerId?.let { put("schema_issuer_id", JsonPrimitive(it)) }
                r.schemaName?.let { put("schema_name", JsonPrimitive(it)) }
                r.schemaVersion?.let { put("schema_version", JsonPrimitive(it)) }
                r.issuerId?.let { put("issuer_id", JsonPrimitive(it)) }
                r.credDefId?.let { put("cred_def_id", JsonPrimitive(it)) }
                r.revRegId?.let { put("rev_reg_id", JsonPrimitive(it)) }
                r.schemaIssuerDid?.let { put("schema_issuer_did", JsonPrimitive(it)) }
                r.issuerDid?.let { put("issuer_did", JsonPrimitive(it)) }

                // attribute markers e values, se existirem
                if (r.attributeMarkers.isNotEmpty()) {
                    r.attributeMarkers.forEach { (k, v) -> put("attr::$k::marker", JsonPrimitive(v)) }
                }
                if (r.attributeValues.isNotEmpty()) {
                    r.attributeValues.forEach { (k, v) -> put("attr::$k::value", JsonPrimitive(v)) }
                }
            }
    }
}
