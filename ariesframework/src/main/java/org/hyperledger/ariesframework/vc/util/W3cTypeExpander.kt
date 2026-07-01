package org.hyperledger.ariesframework.vc.util

import com.github.jsonldjava.core.DocumentLoader
import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
import com.github.jsonldjava.core.RemoteDocument
import com.github.jsonldjava.utils.JsonUtils
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.hyperledger.ariesframework.agent.Agent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/**
 * Expands the "type" values of a W3C VC using jsonld-java.
 *
 * Equivalent to credo-ts `jsonld.expand()`.
 * The documentLoader follows the same resolution order as credo-ts:
 * 1. Static contexts (embedded in W3cStaticContexts)
 * 2. DID resolution
 * 3. HTTP fallback
 */
object W3cTypeExpander {

    private val gson = Gson()

    private const val CONNECT_TIMEOUT_MS = 10000
    private const val READ_TIMEOUT_MS = 30000

    data class ContextSpec(
        val contexts: List<JsonElement>,
    )

    fun expandTypes(
        contextSpec: ContextSpec,
        types: List<String>,
        agent: Agent? = null,
        documentLoader: ((String) -> Any)? = null,
    ): List<String> {
        val document = buildJsonObject {
            put("@context", JsonArray(contextSpec.contexts))
            put("type", JsonArray(types.map { JsonPrimitive(it) }))
        }

        return expandTypes(document.toString(), agent, documentLoader)
    }

    /**
     * Expands the types of a JSON-LD credential.
     *
     * @param documentJson The credential serialized as JSON string.
     * @param agent Optional agent for DID resolution.
     * @return List of expanded types (full IRIs).
     */
    fun expandTypes(documentJson: String, agent: Agent?, documentLoader: ((String) -> Any)? = null): List<String> {
        val input = sanitizeForJsonLd(gson.fromJson(documentJson, Map::class.java) ?: return emptyList())

        val options = JsonLdOptions(JsonLdOptions.JSON_LD_1_1)
        options.documentLoader = JsonLdDocumentLoader(agent, documentLoader)

        @Suppress("UNCHECKED_CAST")
        val expanded = JsonLdProcessor.expand(input, options) as? List<Map<String, Any>>
            ?: return emptyList()

        if (expanded.isEmpty()) return emptyList()

        val types = expanded.first()["@type"]
        return extractTypes(types)
    }

    /**
     * jsonld-java 0.13.6 does not process some JSON-LD 1.1 context keywords
     * correctly on Android regardless of JsonLdOptions processing mode.
     * This function removes context keywords that are not needed for type
     * expansion and that cause the processor to fail.
     */
    private fun sanitizeForJsonLd(input: Map<*, *>): Map<String, Any> {
        val cleaned = sanitizeJsonString(gson.toJson(input))
        @Suppress("UNCHECKED_CAST")
        return gson.fromJson(cleaned, Map::class.java) as Map<String, Any>
    }

    private fun sanitizeJsonString(json: String): String {
        val element = JsonParser.parseString(json)
        removeJsonLdVersion(element)
        return gson.toJson(element)
    }

    private fun removeJsonLdVersion(element: com.google.gson.JsonElement) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                obj.remove("@version")
                obj.remove("@protected")
                sanitizeContainer(obj)
                obj.entrySet().forEach { (_, value) -> removeJsonLdVersion(value) }
            }
            element.isJsonArray -> {
                element.asJsonArray.forEach { removeJsonLdVersion(it) }
            }
        }
    }

    private fun sanitizeContainer(obj: com.google.gson.JsonObject) {
        val container = obj.get("@container") ?: return
        val supportedContainers = setOf("@list", "@set", "@index", "@language")
        val isSupported = when {
            container.isJsonPrimitive -> container.asString in supportedContainers
            container.isJsonArray -> container.asJsonArray.all { it.isJsonPrimitive && it.asString in supportedContainers }
            else -> false
        }

        if (!isSupported) {
            obj.remove("@container")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractTypes(raw: Any?): List<String> = when (raw) {
        is String -> listOf(raw)
        is List<*> -> raw.flatMap { element ->
            when (element) {
                is String -> listOf(element)
                is Map<*, *> -> {
                    val id = (element as Map<String, Any>)["@id"]
                    if (id is String) listOf(id) else emptyList()
                }
                else -> emptyList()
            }
        }.distinct()
        else -> emptyList()
    }

    private class JsonLdDocumentLoader(
        private val agent: Agent?,
        private val configuredDocumentLoader: ((String) -> Any)?,
    ) : DocumentLoader() {

        override fun loadDocument(url: String): RemoteDocument {
            val baseUrl = url.split("#")[0]

            W3cStaticContexts.staticContexts[baseUrl]?.let { jsonStr ->
                val obj = JsonUtils.fromString(sanitizeJsonString(jsonStr))
                return RemoteDocument(url, obj)
            }

            configuredDocumentLoader?.let { loader ->
                val loaded = loader(url)
                if (loaded !is Unit) {
                    val document = when (loaded) {
                        is String -> JsonUtils.fromString(sanitizeJsonString(loaded))
                        else -> JsonUtils.fromString(sanitizeJsonString(loaded.toString()))
                    }
                    return RemoteDocument(url, document)
                }
            }

            if (url.startsWith("did:")) {
                return RemoteDocument(url, mapOf("@id" to url))
            }

            return loadViaHttp(url)
        }

        private fun loadViaHttp(urlString: String): RemoteDocument {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/ld+json, application/json")
            connection.instanceFollowRedirects = true

            val body = try {
                if (connection.responseCode !in 200..299) {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("Unable to load JSON-LD document '$urlString': HTTP ${connection.responseCode}. $errorBody")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }

            val obj = JsonUtils.fromString(sanitizeJsonString(body))
            return RemoteDocument(urlString, obj)
        }
    }
}
