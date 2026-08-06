package org.hyperledger.ariesframework.webvh

import com.google.gson.JsonObject as GsonJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.AnonCredsRegistry
import org.hyperledger.ariesframework.anoncreds.GetRevocationRegistryDefinitionReturn
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList
import org.hyperledger.ariesframework.anoncreds.model.CredentialDefinitionValue
import org.hyperledger.ariesframework.anoncreds.model.GetCredentialDefinitionReturn
import org.hyperledger.ariesframework.anoncreds.model.GetSchemaReturn
import org.hyperledger.ariesframework.anoncreds.service.registry.GetRevocationStatusListReturn
import org.slf4j.LoggerFactory

class WebVhAnonCredsRegistry(
    override val methodName: String = "webvh",
) : AnonCredsRegistry {

    private val logger = LoggerFactory.getLogger(WebVhAnonCredsRegistry::class.java)
    private val resourceHelper = AnonCredsResourceHelper()
    private val json = Json { ignoreUnknownKeys = true }

    override val supportedIdentifier: Regex = Regex("""^did:webvh:.*""")

    override suspend fun getSchema(agent: Agent, schemaId: String): GetSchemaReturn {
        logger.debug("Resolving schema: $schemaId")
        val resource = resourceHelper.fetchResourceByIdentifier(schemaId, isEmulator(agent))
        val content = extractContent(resource)
        val schemaJson = Json.parseToJsonElement(content.toString())
        val schema = json.decodeFromJsonElement<org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema>(schemaJson)
        return GetSchemaReturn(schemaId = schemaId, schema = schema, issuerId = extractIssuerId(schemaId))
    }

    override suspend fun getCredentialDefinition(agent: Agent, credentialDefinitionId: String): GetCredentialDefinitionReturn {
        logger.info("Resolving credential definition via WebVhAnonCredsRegistry: $credentialDefinitionId")
        val resource = resourceHelper.fetchResourceByIdentifier(credentialDefinitionId, isEmulator(agent))
        val content = extractContent(resource)

        val credDefJson = Json.parseToJsonElement(content.toString())
        val value = json.decodeFromJsonElement<CredentialDefinitionValue>(credDefJson.jsonObject["value"]!!)
        val anonCredsCredDef = AnonCredsCredentialDefinition(
            issuerId = extractIssuerId(credentialDefinitionId),
            schemaId = credDefJson.jsonObject["schemaId"]?.jsonPrimitive?.content ?: "",
            type = credDefJson.jsonObject["type"]?.jsonPrimitive?.content ?: "CL",
            tag = credDefJson.jsonObject["tag"]?.jsonPrimitive?.content ?: "default",
            value = value,
        )

        return GetCredentialDefinitionReturn(
            credentialDefinition = anonCredsCredDef,
            credentialDefinitionId = credentialDefinitionId,
        )
    }

    override suspend fun getRevocationRegistryDefinition(
        agent: Agent,
        revocationRegistryDefinitionId: String,
    ): GetRevocationRegistryDefinitionReturn {
        logger.debug("Resolving revocation registry definition: $revocationRegistryDefinitionId")
        val resource = resourceHelper.fetchResourceByIdentifier(revocationRegistryDefinitionId, isEmulator(agent))
        val content = extractContent(resource)
        val revDefJson = Json.parseToJsonElement(content.toString())

        val revDef = json.decodeFromJsonElement<AnonCredsRevocationRegistryDefinition>(revDefJson)
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
        logger.debug("Resolving revocation status list: $revocationRegistryId at timestamp $timestamp")

        // O recurso apontado por revocationRegistryId é o rev_reg_def, que possui
        // "links" (RelatedLink) para os status lists por timestamp.
        val defResource = resourceHelper.fetchResourceByIdentifier(revocationRegistryId, isEmulator(agent))
        val links = defResource.getAsJsonArray("links")

        // Escolhe o link de status list cujo timestamp seja <= ao solicitado (o mais recente)
        val statusLink = links?.firstOrNull {
            val type = it.asJsonObject.get("type")?.asString ?: ""
            type.isNullOrBlank() || type.contains("status", ignoreCase = true)
        }

        if (statusLink == null || statusLink.asJsonObject.get("id") == null) {
            throw RuntimeException(
                "No revocation status list link found on resource $revocationRegistryId. links=$links",
            )
        }

        val statusResourceId = statusLink.asJsonObject.get("id").asString
        val (statusDid, statusResId) = splitResourceIdentifier(statusResourceId)

        val statusResource = resourceHelper.fetchResource(statusDid, statusResId, isEmulator(agent))
        val content = extractContent(statusResource)
        android.util.Log.e("WEBVH_STATUS_LIST", "status list content for $statusResourceId:\n$content")

        val statusListJson = Json.parseToJsonElement(content.toString())
        val statusList = json.decodeFromJsonElement<AnonCredsRevocationStatusList>(statusListJson)
        return GetRevocationStatusListReturn(revocationStatusList = statusList)
    }

    private fun splitResourceIdentifier(identifier: String): Pair<String, String> {
        val parts = identifier.split("/resources/")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid did:webvh resource identifier: $identifier")
        }
        return parts[0] to parts[1]
    }

    private fun extractContent(resource: GsonJsonObject): GsonJsonObject {
        val content = resource.getAsJsonObject("content")
            ?: throw RuntimeException("Attested resource missing 'content' field")
        return content
    }

    private fun extractIssuerId(identifier: String): String {
        return identifier.substringBefore("/resources/")
    }

    private fun isEmulator(agent: Agent): Boolean {
        return WebVhEmulatorWorkaround.isEnabled(agent)
    }
}
