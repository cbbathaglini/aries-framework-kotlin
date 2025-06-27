package org.hyperledger.ariesframework.vc.dataintegrity

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential
import org.hyperledger.ariesframework.vc.modules.W3cCredentialsModuleConfig

class W3cJsonLdCredentialService(
    private val agent: Agent,
    private val w3cCredentialsModuleConfig: W3cCredentialsModuleConfig
) {

    // [TODO] revisar a implementacao
    suspend fun getExpandedTypesForCredential(
        credential: W3cJsonLdVerifiableCredential
    ): Map<String, String> {
        val credentialJson = credential.toJsonString()
        val documentLoader = w3cCredentialsModuleConfig.documentLoader

        val jsonElementCredential = Json.parseToJsonElement(credentialJson)
        val expanded = expandJsonLd(
            jsonElementCredential,
            mapOf("documentLoader" to documentLoader)
        )

        val firstExpanded = (expanded as? List<Map<String, Any?>>)?.firstOrNull()
        val types = firstExpanded?.get("@type")

        return when (types) {
            is String -> listOf(types)
            is List<*> -> types.filterIsInstance<String>()
            else -> emptyList()
        }
    }


    suspend fun expandJsonLd(
        input: JsonElement,
        documentLoader: suspend (String) -> JsonObject
    ): List<JsonObject> {
        val context = extractAndResolveContext(input, documentLoader)
        val expanded = expandElement(input, context)

        return listOfNotNull(expanded as? JsonObject)
    }

    suspend fun extractAndResolveContext(
        input: JsonElement,
        documentLoader: suspend (String) -> JsonObject
    ): Map<String, TermDefinition> {
        val context = (input as? JsonObject)?.get("@context") ?: return emptyMap()

        return when (context) {
            is JsonPrimitive -> {
                val iri = context.content
                val remoteContext = documentLoader(iri)
                parseContext(remoteContext)
            }
            is JsonObject -> parseContext(context)
            else -> emptyMap()
        }
    }

    data class TermDefinition(
        val iri: String,
        val type: String? = null // "@id", "@vocab", etc
    )

    fun parseContext(contextObj: JsonObject): Map<String, TermDefinition> {
        val definitions = mutableMapOf<String, TermDefinition>()
        contextObj.forEach { (term, definition) ->
            when (definition) {
                is JsonPrimitive -> {
                    definitions[term] = TermDefinition(iri = definition.content)
                }
                is JsonObject -> {
                    val iri = definition["@id"]?.jsonPrimitive?.content
                    val type = definition["@type"]?.jsonPrimitive?.content
                    if (iri != null) {
                        definitions[term] = TermDefinition(iri, type)
                    }
                }
                else -> {}
            }
        }
        return definitions
    }

    fun expandElement(input: JsonElement, context: Map<String, TermDefinition>): JsonElement {
        return when (input) {
            is JsonObject -> expandObject(input, context)
            is JsonArray -> JsonArray(input.map { expandElement(it, context) })
            else -> input
        }
    }

    fun expandObject(obj: JsonObject, context: Map<String, TermDefinition>): JsonObject {
        val result = mutableMapOf<String, JsonElement>()

        obj.forEach { (key, value) ->
            if (key == "@context") return@forEach // Ignorar contexto

            val expandedKey = context[key]?.iri ?: key
            val expandedValue = expandElement(value, context)

            result[expandedKey] = when (context[key]?.type) {
                "@id" -> JsonObject(mapOf("@id" to expandedValue))
                else -> when (expandedValue) {
                    is JsonPrimitive -> JsonObject(mapOf("@value" to expandedValue))
                    else -> expandedValue
                }
            }
        }

        return JsonObject(result)
    }
}