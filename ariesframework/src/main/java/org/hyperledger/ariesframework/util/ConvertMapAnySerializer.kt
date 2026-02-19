package org.hyperledger.ariesframework.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class ConvertMapAnySerializer {
    companion object {
        fun mapAnyToJsonElement(map: Map<String, Any?>): JsonElement {
            return buildJsonObject {
                for ((key, value) in map) {
                    put(key, anyToJsonElement(value))
                }
            }
        }

        fun anyToJsonElement(value: Any?): JsonElement {
            return when (value) {
                null -> JsonNull
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is Map<*, *> -> {
                    val map = value.entries.associate { (k, v) -> k.toString() to v }
                    mapAnyToJsonElement(map)
                }
                is List<*> -> JsonArray(value.map { anyToJsonElement(it!!) })
                else -> JsonPrimitive(value.toString()) // Fallback (could also throw)
            }
        }
    }
}
