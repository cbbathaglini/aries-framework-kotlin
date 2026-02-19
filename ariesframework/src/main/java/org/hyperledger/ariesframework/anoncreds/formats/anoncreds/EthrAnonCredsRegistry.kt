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

class EthrAnonCredsRegistry(override val methodName: String = "ethr") : AnonCredsRegistry {

    private val logger = LoggerFactory.getLogger(EthrAnonCredsRegistry::class.java)

    /*private val ETHR_ANONCREDS_REGEX =
        Regex("""^did:ethr:[^/]+/anoncreds/v0/(SCHEMA|CRED_DEF|REV_REG_DEF|REV_REG)/[^/]+/[0-9]+(?:\.[0-9]+)*$""", RegexOption.IGNORE_CASE)*/

    // val ETHR_ANONCREDS_REGEX = Regex("""^did:ethr:[^/]+/anoncreds/v0/(?:SCHEMA/[^/]+/[0-9]+(?:\.[0-9]+)*|CRED_DEF/[^/]+|REV_REG_DEF/.+?/(?:CL_ACCUM(?::|/)[A-Za-z0-9._-]+)|REV_REG/[^/]+)$""".trimIndent().replace(Regex("""\s+"""), ""), RegexOption.IGNORE_CASE)
    // val ETHR_ANONCREDS_REGEX = Regex("^did:ethr:[^/]+/anoncreds/v0/(?:SCHEMA/[^/]+/\\d+(?:\\.\\d+)*|CRED_DEF/[^/]+|REV_REG_DEF/[^/]+/[^/]+/\\d+|REV_REG/[^/]+/CL_ACCUM(?::|/)[A-Za-z0-9._-]+)\$", RegexOption.IGNORE_CASE)
    val ETHR_ANONCREDS_REGEX = Regex(
        "^did:ethr:[^/]+/anoncreds/v0/(?:SCHEMA/[^/]+/\\d+(?:\\.\\d+)*|CRED_DEF/[^/]+|REV_REG_DEF/[^/]+/[^/]+/(?:\\d+|CL_ACCUM(?::|/)[A-Za-z0-9._-]+)|REV_REG/[^/]+/CL_ACCUM(?::|/)[A-Za-z0-9._-]+)\$",
        RegexOption.IGNORE_CASE,
    )

    override val supportedIdentifier: Regex = ETHR_ANONCREDS_REGEX

    override suspend fun getSchema(agent: Agent, schemaId: String): GetSchemaReturn {
        require(supportedIdentifier.matches(schemaId)) {
            "Schema id não suportado por EthrAnonCredsRegistry: $schemaId"
        }

        val (name, version) = parseEthrSchemaId(schemaId)
        val fetchSchemaReturn_aux = agent.ledgerService.getSchema(schemaId)
        val jsonElementSchema: JsonElement = Json.parseToJsonElement(fetchSchemaReturn_aux.first)
        val fetchSchemaReturn: FetchSchemaReturn = FetchSchemaReturn.fromJson(jsonElementSchema, schemaId)

        val issuerId = extractDidEthr(schemaId)
        return GetSchemaReturn(
            schemaId = schemaId,
            schema = fetchSchemaReturn.schema,
            issuerId = issuerId,
        )
    }

    override suspend fun getCredentialDefinition(agent: Agent, credentialDefinitionId: String): GetCredentialDefinitionReturn {
        val credentialDefinitionVdr = agent.ledgerService.getCredentialDefinitionvVdr(credentialDefinitionId)

        val credentialDefinitionValue = Json.decodeFromString<CredentialDefinitionValue>(credentialDefinitionVdr.value)

        val anonCredsCredentialDefinition: AnonCredsCredentialDefinition = AnonCredsCredentialDefinition(
            issuerId = credentialDefinitionVdr.issuerId,
            schemaId = credentialDefinitionVdr.schemaId,
            type = credentialDefinitionVdr.credDefType,
            tag = credentialDefinitionVdr.tag,
            value = credentialDefinitionValue,
        )

        return GetCredentialDefinitionReturn(
            credentialDefinition = anonCredsCredentialDefinition,
            credentialDefinitionId = credentialDefinitionId,
        )
    }

    private fun extractCredentialDefinitionId(revRegDefId: String): String? {
        val parts = revRegDefId.split("REV_REG_DEF/")
        if (parts.size <= 1) return null

        return parts[1].split("/").firstOrNull()
    }

    override suspend fun getRevocationRegistryDefinition(
        agent: Agent,
        revocationRegistryDefinitionId: String,
    ): GetRevocationRegistryDefinitionReturn {
        val revocationJson = agent.ledgerService
            .getRevocationRegistryDefinition(revocationRegistryDefinitionId)

        val revocationRegistryResult =
            Json.decodeFromString<FetchIntermediateRevocationRegistryDefinitionResult>(revocationJson)
                .copy(revocationRegistryDefinitionId = revocationRegistryDefinitionId)

        val revRegId = revocationRegistryResult.revocationRegistryDefinitionId
            ?: throw CredoError("Invalid revocation registry definition response")

        val credentialDefinitionId =
            extractCredentialDefinitionId(revocationRegistryDefinitionId)
                ?: throw CredoError("Could not extract credential definition ID")

        val revDef = AnonCredsRevocationRegistryDefinition(
            issuerId = revocationRegistryResult.issuerId,
            revocDefType = revocationRegistryResult.revocDefType,
            credDefId = revocationRegistryResult.credDefId,
            tag = revocationRegistryResult.tag,
            value = revocationRegistryResult.value,
        )

        return GetRevocationRegistryDefinitionReturn(
            revocationRegistryDefinition = revDef,
            revocationRegistryDefinitionId = revocationRegistryDefinitionId,
        )
    }

    override suspend fun getRevocationStatusList(
        agent: Agent,
        revocationRegistryId: String,
        timestamp: ULong,
    ): GetRevocationStatusListReturn {
        try {
            val revocationStatusList: RevocationStatusList =
                agent.ledgerService.getRevocationStatusList(
                    revocationRegistryId,
                    timestamp,
                )

            val revocationStatusListInts: List<Int> =
                revocationStatusList.revocationList.map { it.toInt() }
            val anoncredsRevocationStatusList = AnonCredsRevocationStatusList(
                issuerId = revocationStatusList.issuerId,
                revRegDefId = revocationStatusList.revRegDefId,
                revocationList = revocationStatusListInts,
                currentAccumulator = revocationStatusList.currentAccumulator,
                timestamp = revocationStatusList.timestamp,
            )
            return GetRevocationStatusListReturn(
                revocationStatusList = anoncredsRevocationStatusList,
            )
        } catch (e: Exception) {
            LogUtil.error(this, e) { "getRevocationStatusList error: ${e.message}" }
            throw e
        }
    }

    private fun parseEthrSchemaId(schemaId: String): Pair<String, String> {
        val parts = schemaId.split('/')
        val version = parts.last()
        val name = parts[parts.size - 2]
        return name to version
    }

    private fun extractDidEthr(schemaId: String): String? {
        val m = Regex("""^did:ethr:[^/]+""", RegexOption.IGNORE_CASE).find(schemaId)
        return m?.value
    }
}
