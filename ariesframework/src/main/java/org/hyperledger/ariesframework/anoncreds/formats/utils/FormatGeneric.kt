package org.hyperledger.ariesframework.anoncreds.formats.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class FormatGeneric {
    companion object {
        inline fun <reified T> getAnonCredsFormatGeneric(
            formats: Map<String, JsonElement>?,
        ): T {
            val json = Json {
                ignoreUnknownKeys = true
            }

            val anonCredsJson = formats?.get("anoncreds")
                ?: throw IllegalArgumentException("Missing 'anoncreds' format")

            return json.decodeFromJsonElement(anonCredsJson)
        }

        inline fun <reified T> getLegacyIndyFormatGeneric(
            formats: Map<String, JsonElement>?,
        ): T {
            val json = Json {
                ignoreUnknownKeys = true
            }

            val anonCredsJson = formats?.get("indy")
                ?: throw IllegalArgumentException("Missing 'indy' format")

            return json.decodeFromJsonElement(anonCredsJson)
        }
    }
}
