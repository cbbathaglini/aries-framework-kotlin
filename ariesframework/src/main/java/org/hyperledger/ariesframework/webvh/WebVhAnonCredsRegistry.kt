package org.hyperledger.ariesframework.webvh

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
import com.google.gson.JsonObject as GsonJsonObject

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

        // The resource pointed to by revocationRegistryId is the rev_reg_def, which has
        // "links" (RelatedLink) to the status lists per timestamp.
        val defResource = resourceHelper.fetchResourceByIdentifier(revocationRegistryId, isEmulator(agent))
        val links = defResource.getAsJsonArray("links")

        // Collect all status list links.
        val statusLinks = links?.mapNotNull { element ->
            val obj = element.asJsonObject
            val type = obj.get("type")?.asString ?: ""
            if (type.isNullOrBlank() || type.contains("status", ignoreCase = true)) {
                obj.get("id")?.asString?.let { it to obj }
            } else {
                null
            }
        } ?: emptyList()

        if (statusLinks.isEmpty()) {
            throw RuntimeException(
                "No revocation status list link found on resource $revocationRegistryId. links=$links",
            )
        }

        // Fetch the status list for every link and choose the one whose timestamp is the most
        // recent among those <= the requested timestamp. The status list resource 'content'
        // carries its own timestamp; using it (instead of a link field) is robust regardless of
        // how each link is annotated.
        val candidates = statusLinks.mapNotNull { (statusResourceId, _) ->
            val (statusDid, statusResId) = splitResourceIdentifier(statusResourceId)
            val statusResource = resourceHelper.fetchResource(statusDid, statusResId, isEmulator(agent))
            val content = extractContent(statusResource)
            val statusListJson = Json.parseToJsonElement(content.toString())
            val statusList = try {
                json.decodeFromJsonElement<AnonCredsRevocationStatusList>(statusListJson)
            } catch (e: Exception) {
                logger.warn("Failed to decode revocation status list from $statusResourceId: ${e.message}")
                null
            }
            statusList?.let { Triple(statusResourceId, statusList, content) }
        }

        if (candidates.isEmpty()) {
            throw RuntimeException(
                "Could not decode any revocation status list for $revocationRegistryId. links=$links",
            )
        }

        val (statusResourceId, statusList, statusContent) = candidates
            .filter { it.second.timestamp <= timestamp }
            .maxByOrNull { it.second.timestamp }
            ?: candidates.minByOrNull { it.second.timestamp }!!

        android.util.Log.e("WEBVH_STATUS_LIST", "status list content for $statusResourceId (list timestamp=${statusList.timestamp}, requested=$timestamp):\n$statusContent")

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
