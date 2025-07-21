package org.hyperledger.ariesframework.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class ConvertFromAny {
    companion object{
        inline fun <reified T> convertAnyToSerializable(anyValue: Any): T {
            val json = Json { ignoreUnknownKeys = true }

            val jsonElement = when (anyValue) {
                is JsonElement -> anyValue
                else -> json.encodeToJsonElement(anyValue)
            }

            return json.decodeFromJsonElement(jsonElement)
        }
    }
}