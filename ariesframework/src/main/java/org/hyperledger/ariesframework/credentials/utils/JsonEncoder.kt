package org.hyperledger.ariesframework.credentials.utils

import android.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object JsonEncoder {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun toBase64(data: Any): String {
        val jsonString = when (data) {
            is JsonElement -> json.encodeToString(JsonElement.serializer(), data)
            is JsonObject -> json.encodeToString(JsonObject.serializer(), data)
            else -> throw IllegalArgumentException("Only JsonElement or JsonObject supported for base64 encoding")
        }

        return Base64.encodeToString(jsonString.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
}
