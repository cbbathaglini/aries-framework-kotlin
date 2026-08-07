package org.hyperledger.ariesframework.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

class JsonUtils {
    companion object {

        fun Map<String, Any?>.toJsonString(pretty: Boolean = false): String {
            val jsonElement = this.toJsonElement()
            return if (pretty) {
                Json { prettyPrint = true }.encodeToString(jsonElement)
            } else
                Json.encodeToString(jsonElement)
        }

        fun convertMapToJson(map: Map<String, Any?>): JsonObject {
            val jsonObject = JsonObject(
                map.mapValues { (_, v) ->
                    when (v) {
                        null -> JsonNull
                        is Boolean -> JsonPrimitive(v)
                        is Number -> JsonPrimitive(v)
                        is String -> JsonPrimitive(v)
                        is JsonElement -> v
                        else -> Json.encodeToJsonElement(v) // uses kotlinx.serialization if the type is @Serializable
                    }
                },
            )
            return jsonObject
        }

        fun JsonElement.toJsonMap(): Map<String, Any?> =
            when (this) {
                is JsonObject -> this.mapValues { (_, v) -> v.toKotlinValue() }
                else -> error("Expected JsonObject")
            }

        fun Map<String, Any?>.toJsonElement(): JsonElement {
            val content = this.mapValues { (_, value) ->
                when (value) {
                    null -> JsonNull
                    is String -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is Boolean -> JsonPrimitive(value)
                    is Map<*, *> -> (value as Map<String, Any?>).toJsonElement()
                    is List<*> -> JsonArray(
                        value.map {
                            when (it) {
                                null -> JsonNull
                                is String -> JsonPrimitive(it)
                                is Number -> JsonPrimitive(it)
                                is Boolean -> JsonPrimitive(it)
                                is Map<*, *> -> (it as Map<String, Any?>).toJsonElement()
                                else -> JsonPrimitive(it.toString())
                            }
                        },
                    )
                    else -> JsonPrimitive(value.toString())
                }
            }

            return JsonObject(content)
        }

        fun String.parseJsonMap(): Map<String, Any?> {
            val root = Json.parseToJsonElement(this)
            return root.toKotlinValue() as Map<String, Any?>
        }

        fun JsonElement.toKotlinValue(): Any? =
            when (this) {
                is JsonObject ->
                    this.mapValues { (_, v) -> v.toKotlinValue() }

                is JsonArray ->
                    this.map { it.toKotlinValue() }

                is JsonPrimitive -> when {
                    this.isString -> this.content
                    this.booleanOrNull != null -> this.boolean
                    this.longOrNull != null -> this.long
                    this.doubleOrNull != null -> this.double
                    else -> this.content
                }

                else -> null
            }

        /**
         * Converte JsonObject → Map<String, Any?>
         */
        fun JsonObject.toMutableMap(): MutableMap<String, Any?> {
            val map = mutableMapOf<String, Any?>()

            for ((key, value) in this) {
                map[key] = when {
                    value.isString() -> value.jsonPrimitive.content
                    value.jsonPrimitive.intOrNull != null -> value.jsonPrimitive.int
                    value.jsonPrimitive.longOrNull != null -> value.jsonPrimitive.long
                    value.jsonPrimitive.doubleOrNull != null -> value.jsonPrimitive.double
                    value.jsonPrimitive.booleanOrNull != null -> value.jsonPrimitive.boolean
                    value is JsonObject -> value.toMutableMap()
                    else -> value.toString() // fallback
                }
            }

            return map
        }

        private fun kotlinx.serialization.json.JsonElement.isString() =
            this is kotlinx.serialization.json.JsonPrimitive && this.isString
        fun mapToJson(map: Map<String, Any?>): JsonObject {
            val content = map.mapValues { (_, value) ->
                when (value) {
                    null -> JsonNull
                    is JsonElement -> value
                    is String -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is Boolean -> JsonPrimitive(value)
                    is Map<*, *> -> mapToJson(value as Map<String, Any?>)
                    is List<*> -> JsonArray(
                        value.map { v ->
                            when (v) {
                                null -> JsonNull
                                is JsonElement -> v
                                is String -> JsonPrimitive(v)
                                is Number -> JsonPrimitive(v)
                                is Boolean -> JsonPrimitive(v)
                                is Map<*, *> -> mapToJson(v as Map<String, Any?>)
                                else -> JsonPrimitive(v.toString())
                            }
                        },
                    )

                    else -> JsonPrimitive(value.toString())
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

            // Maps (including MutableMap<String, JsonObject> etc.)
            is Map<*, *> -> {
                // If it's already <String, JsonElement>
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
