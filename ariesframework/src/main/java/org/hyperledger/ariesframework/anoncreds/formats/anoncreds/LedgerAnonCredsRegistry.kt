package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.AnonCredsRegistry
import org.hyperledger.ariesframework.anoncreds.GetRevocationRegistryDefinitionReturn
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList
import org.hyperledger.ariesframework.anoncreds.model.CredentialDefinitionValue
import org.hyperledger.ariesframework.anoncreds.model.FetchIntermediateRevocationRegistryDefinitionResult
import org.hyperledger.ariesframework.anoncreds.model.FetchSchemaReturn
import org.hyperledger.ariesframework.anoncreds.model.GetCredentialDefinitionReturn
import org.hyperledger.ariesframework.anoncreds.model.GetSchemaReturn
import org.hyperledger.ariesframework.anoncreds.service.registry.GetRevocationStatusListReturn
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.util.LogUtil
import org.slf4j.LoggerFactory
import uniffi.indy_besu_vdr.RevocationStatusList

class LedgerAnonCredsRegistry(override val methodName: String = "ledger") : AnonCredsRegistry {

    private val logger = LoggerFactory.getLogger(LedgerAnonCredsRegistry::class.java)

    override val supportedIdentifier: Regex = Regex(".+")

    override suspend fun getSchema(agent: Agent, schemaId: String): GetSchemaReturn {
        val (schemaJson, _) = agent.ledgerService.getSchema(schemaId)
        val jsonElement = Json.parseToJsonElement(schemaJson)
        val fetchResult = FetchSchemaReturn.fromJson(jsonElement, schemaId)

        val issuerId = fetchResult.schema.issuerId ?: schemaId.split(':').first()
        return GetSchemaReturn(
            schemaId = schemaId,
            schema = fetchResult.schema,
            issuerId = issuerId,
        )
    }

    override suspend fun getCredentialDefinition(agent: Agent, credentialDefinitionId: String): GetCredentialDefinitionReturn {
        val vdr = agent.ledgerService.getCredentialDefinitionvVdr(credentialDefinitionId)
        val value = Json.decodeFromString<CredentialDefinitionValue>(vdr.value)

        return GetCredentialDefinitionReturn(
            credentialDefinition = AnonCredsCredentialDefinition(
                issuerId = vdr.issuerId,
                schemaId = vdr.schemaId,
                type = vdr.credDefType,
                tag = vdr.tag,
                value = value,
            ),
            credentialDefinitionId = credentialDefinitionId,
        )
    }

    override suspend fun getRevocationRegistryDefinition(
        agent: Agent,
        revocationRegistryDefinitionId: String,
    ): GetRevocationRegistryDefinitionReturn {
        val json = agent.ledgerService.getRevocationRegistryDefinition(revocationRegistryDefinitionId)
        val result = Json.decodeFromString<FetchIntermediateRevocationRegistryDefinitionResult>(json)
            .copy(revocationRegistryDefinitionId = revocationRegistryDefinitionId)

        return GetRevocationRegistryDefinitionReturn(
            revocationRegistryDefinition = AnonCredsRevocationRegistryDefinition(
                issuerId = result.issuerId,
                revocDefType = result.revocDefType,
                credDefId = result.credDefId,
                tag = result.tag,
                value = result.value,
            ),
            revocationRegistryDefinitionId = revocationRegistryDefinitionId,
        )
    }

    override suspend fun getRevocationStatusList(
        agent: Agent,
        revocationRegistryId: String,
        timestamp: ULong,
    ): GetRevocationStatusListReturn {
        try {
            val statusList: RevocationStatusList = agent.ledgerService.getRevocationStatusList(
                revocationRegistryId, timestamp,
            )
            return GetRevocationStatusListReturn(
                revocationStatusList = AnonCredsRevocationStatusList(
                    issuerId = statusList.issuerId,
                    revRegDefId = statusList.revRegDefId,
                    revocationList = statusList.revocationList.map { it.toInt() },
                    currentAccumulator = statusList.currentAccumulator,
                    timestamp = statusList.timestamp,
                ),
            )
        } catch (e: Exception) {
            LogUtil.error(this, e) { "getRevocationStatusList error: ${e.message}" }
            throw e
        }
    }
}
