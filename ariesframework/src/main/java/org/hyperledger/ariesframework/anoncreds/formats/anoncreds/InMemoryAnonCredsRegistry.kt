package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import jnr.ffi.annotations.In
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.AnonCredsRegistry
import org.hyperledger.ariesframework.anoncreds.GetRevocationRegistryDefinitionReturn
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsResolutionMetadata
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.anoncreds.model.GetCredentialDefinitionReturn
import org.hyperledger.ariesframework.anoncreds.model.GetSchemaReturn
import org.hyperledger.ariesframework.anoncreds.service.registry.GetRevocationStatusListReturn
import org.hyperledger.ariesframework.anoncreds.utils.Indyidentifiers

//class InMemoryAnonCredsRegistry(override val methodName: String = "inMemory",
//                                override val supportedIdentifier: Regex = Regex(".+"),
//                                existingSchemas: MutableMap<String, AnonCredsSchema> = mutableMapOf(),
//                                existingCredentialDefinitions: MutableMap<String, AnonCredsCredentialDefinition> = mutableMapOf(),
//                                existingRevocationRegistryDefinitions: MutableMap<String, AnonCredsRevocationRegistryDefinition> = mutableMapOf(),
//                                existingRevocationStatusLists: MutableMap<String, MutableMap<String, AnonCredsRevocationStatusList>> = mutableMapOf()
//) : AnonCredsRegistry{
//
//    private val schemas: MutableMap<String, AnonCredsSchema> = existingSchemas
//    private val credentialDefinitions: MutableMap<String, AnonCredsCredentialDefinition> = existingCredentialDefinitions
//    private val revocationRegistryDefinitions: MutableMap<String, AnonCredsRevocationRegistryDefinition> =
//        existingRevocationRegistryDefinitions
//    private val revocationStatusLists: MutableMap<String, MutableMap<String, AnonCredsRevocationStatusList>> =
//        existingRevocationStatusLists
//
//    override suspend fun getSchema(
//        agent: Agent,
//        schemaId: String
//    ): GetSchemaReturn {
//        val schema = schemas[schemaId]
//
//        if (schema == null) {
//            return GetSchemaReturn(
//                resolutionMetadata = AnonCredsResolutionMetadata(
//                    error = "not found",
//                    message = "Schema with id $schemaId not found in memory registry"
//                ),
//                schemaId = schemaId,
//                schemaMetadata = emptyMap()
//            )
//        }
//
//        var didIndyNamespace: String? = null
//        if (Indyidentifiers.isUnqualifiedSchemaId(schemaId)) {
//            val (namespaceIdentifier, schemaName, schemaVersion) = Indyidentifiers.parseIndySchemaId(schemaId)
//            val qualifiedSchemaEnding = "$namespaceIdentifier/anoncreds/v0/SCHEMA/$schemaName/$schemaVersion"
//            val qualifiedSchemaId = schemas.keys.find { it.endsWith(qualifiedSchemaEnding) }
//            didIndyNamespace = qualifiedSchemaId?.let { Indyidentifiers.parseIndySchemaId(it).first }
//        } else if (Indyidentifiers.isIndyDid(schemaId)) {
//            didIndyNamespace = Indyidentifiers.parseIndySchemaId(schemaId).first
//        }
//
//        val schemaMetadata = if (didIndyNamespace != null) {
//            mapOf("didIndyNamespace" to didIndyNamespace)
//        } else emptyMap()
//
//        return GetSchemaReturn(
//            schema = schema,
//            schemaId = schemaId,
//            schemaMetadata = schemaMetadata
//        )
//    }
//
//    override suspend fun getCredentialDefinition(agent: Agent, credentialDefinitionId: String): GetCredentialDefinitionReturn {
//        val credentialDefinition = credentialDefinitions[credentialDefinitionId]
//
//        if (credentialDefinition == null) {
//            return GetCredentialDefinitionReturn(
//                resolutionMetadata = AnonCredsResolutionMetadata(
//                    error = "not found",
//                    message = "Credential definition with id $credentialDefinitionId not found in memory registry"
//                ),
//                credentialDefinitionId = credentialDefinitionId,
//                credentialDefinitionMetadata = emptyMap()
//            )
//        }
//
//        var didIndyNamespace: String? = null
//        if (Indyidentifiers.isUnqualifiedCredentialDefinitionId(credentialDefinitionId)) {
//            val (namespaceIdentifier, schemaSeqNo, tag) = Indyidentifiers.parseIndyCredentialDefinitionId(credentialDefinitionId)
//            val qualifiedCredDefEnding = "$namespaceIdentifier/anoncreds/v0/CLAIM_DEF/$schemaSeqNo/$tag"
//
//            val unqualifiedCredDefId = credentialDefinitions.keys.find { it.endsWith(qualifiedCredDefEnding) }
//
//            didIndyNamespace = unqualifiedCredDefId?.let {
//                Indyidentifiers.parseIndyCredentialDefinitionId(it).first
//            }
//        } else if (Indyidentifiers.isIndyDid(credentialDefinitionId)) {
//            didIndyNamespace = Indyidentifiers.parseIndyCredentialDefinitionId(credentialDefinitionId).first
//        }
//
//        val metadata = didIndyNamespace?.let { mapOf("didIndyNamespace" to it) } ?: emptyMap()
//
//        return GetCredentialDefinitionReturn(
//            credentialDefinition = credentialDefinition,
//            credentialDefinitionId = credentialDefinitionId,
//            credentialDefinitionMetadata = metadata
//        )
//    }
//
//    override suspend fun getRevocationRegistryDefinition(revocationRegistryDefinitionId: String): GetRevocationRegistryDefinitionReturn {
//        val revocationRegistryDefinition = revocationRegistryDefinitions[revocationRegistryDefinitionId]
//
//        if (revocationRegistryDefinition == null) {
//            return GetRevocationRegistryDefinitionReturn(
//                resolutionMetadata = AnonCredsResolutionMetadata(
//                    error = "not found",
//                    message = "Revocation registry definition with id $revocationRegistryDefinitionId not found in memory registry"
//                ),
//
//                revocationRegistryDefinitionId = revocationRegistryDefinitionId,
//                revocationRegistryDefinitionMetadata = emptyMap()
//            )
//        }
//
//        var didIndyNamespace: String? = null
//
//        if (Indyidentifiers.isUnqualifiedCredentialDefinitionId(revocationRegistryDefinitionId)) {
//            // parseIndyRevocationRegistryId deve retornar algo como:
//            // data class RevRegIdParts(val namespaceIdentifier: String, val schemaSeqNo: String, val revocationRegistryTag: String, val namespace: String?)
//            val parts = Indyidentifiers.parseIndyRevocationRegistryId(revocationRegistryDefinitionId)
//            val qualifiedRevRegIdEnding =
//                ":${parts.namespaceIdentifier}/anoncreds/v0/REV_REG_DEF/${parts.schemaSeqNo}/${parts.revocationRegistryTag}"
//
//            val unqualifiedRevRegId = revocationRegistryDefinitions.keys.find { it.endsWith(qualifiedRevRegIdEnding) }
//
//            // Nota: o TS chama parseIndySchemaId aqui; mantive o mesmo comportamento.
//            didIndyNamespace = unqualifiedRevRegId?.let { Indyidentifiers.parseIndySchemaId(it).first }
//        } else if (Indyidentifiers.isIndyDid(revocationRegistryDefinitionId)) {
//            didIndyNamespace = Indyidentifiers.parseIndyRevocationRegistryId(revocationRegistryDefinitionId).namespace
//        }
//
//        val meta = didIndyNamespace?.let { mapOf("didIndyNamespace" to it as JsonElement) } ?: emptyMap()
//
//        return GetRevocationRegistryDefinitionReturn(
//            revocationRegistryDefinition = revocationRegistryDefinition,
//            revocationRegistryDefinitionId = revocationRegistryDefinitionId,
//            revocationRegistryDefinitionMetadata = meta
//        )
//    }
//
//    override suspend fun getRevocationStatusList(
//        revocationRegistryId: String,
//        timestamp: Long
//    ): GetRevocationStatusListReturn {
//        val revocationStatusListsForReg: Map<String, AnonCredsRevocationStatusList>? =
//            revocationStatusLists.get(revocationRegistryId)
//
//        if (revocationStatusListsForReg == null || revocationStatusListsForReg.isEmpty()) {
//            return GetRevocationStatusListReturn(
//                resolutionMetadata = AnonCredsResolutionMetadata(
//                    error = "not found",
//                    message = "Revocation status list for revocation registry with id $revocationRegistryId not found in memory registry"
//                ),
//                revocationStatusListMetadata = emptyMap()
//            )
//        }
//
//        // timestamps <= timestamp solicitado
//        val previousTimestamps: List<Long> =
//            revocationStatusListsForReg.keys
//                .mapNotNull { it.toLongOrNull() }
//                .filter { it <= timestamp }
//                .sorted()
//
//        if (previousTimestamps.isEmpty()) {
//            return GetRevocationStatusListReturn(
//                resolutionMetadata = AnonCredsResolutionMetadata(
//                    error = "not found",
//                    message = "No active Revocation status list found at $timestamp for revocation registry with id $revocationRegistryId"
//                ),
//                revocationStatusListMetadata = emptyMap()
//            )
//        }
//
//        val chosenTs = previousTimestamps.last() // o maior ts <= solicitado
//        val statusList = revocationStatusListsForReg[chosenTs.toString()]
//
//        return GetRevocationStatusListReturn(
//            revocationStatusList = statusList,
//            revocationStatusListMetadata = emptyMap()
//        )
//    }
//}