package org.hyperledger.ariesframework.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

class JsonUtils {
    companion object {
        fun convertMapToJson(map: Map<String, Any?>): JsonObject {
            val jsonObject = JsonObject(
                map.mapValues { (_, v) ->
                    when (v) {
                        null -> JsonNull
                        is Boolean -> JsonPrimitive(v)
                        is Number -> JsonPrimitive(v)
                        is String -> JsonPrimitive(v)
                        is JsonElement -> v
                        else -> Json.encodeToJsonElement(v) // usa kotlinx.serialization se o tipo for @Serializable
                    }
                }
            )
            return jsonObject
        }

        fun mapToJson(map: Map<String, Any?>): JsonObject {
            val content = map.mapValues { (_, value) ->
                when (value) {
                    null -> JsonNull
                    is JsonElement -> value
                    is String -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is Boolean -> JsonPrimitive(value)
                    is Map<*, *> -> mapToJson(value as Map<String, Any?>) // recursão
                    is List<*> -> JsonArray(value.map { v ->
                        when (v) {
                            null -> JsonNull
                            is JsonElement -> v
                            is String -> JsonPrimitive(v)
                            is Number -> JsonPrimitive(v)
                            is Boolean -> JsonPrimitive(v)
                            is Map<*, *> -> mapToJson(v as Map<String, Any?>)
                            else -> JsonPrimitive(v.toString()) // fallback
                        }
                    })

                    else -> JsonPrimitive(value.toString()) // fallback pra tipos desconhecidos
                }
            }
            return JsonObject(content)
        }


        fun elementToJson(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)

            // Mapas (inclui MutableMap<String, JsonObject> etc.)
            is Map<*, *> -> {
                // Se já for <String, JsonElement>
                if (value.keys.all { it is String } && value.values.all { it is JsonElement }) {
                    @Suppress("UNCHECKED_CAST")
                    JsonObject(value as Map<String, JsonElement>)
                } else {
                    // Recursivo
                    @Suppress("UNCHECKED_CAST")
                    mapToJson(value as Map<String, Any?>)
                }
            }

            // Listas (inclui List<CredentialEntry>)
            is List<*> -> JsonArray(value.map { elementToJson(it) })

            else -> JsonPrimitive(value.toString()) // fallback
        }

        fun mapToJson2(map: Map<String, Any?>): JsonObject =
            JsonObject(map.mapValues { (_, v) -> elementToJson(v) })
    }
}