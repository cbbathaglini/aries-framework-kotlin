package org.hyperledger.ariesframework.anoncreds.formats.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class FormatGeneric {
    companion object {
        inline fun <reified T> getAnonCredsFormatGeneric(
            credentialFormats: Map<String, JsonElement>?,
        ): T {
            val json = Json {
                ignoreUnknownKeys = true
            }

            val anonCredsJson = credentialFormats?.get("anoncreds")
                ?: throw IllegalArgumentException("Missing 'anoncreds' credential format")

            return json.decodeFromJsonElement(anonCredsJson)
        }

        inline fun <reified T> getLegacyIndyFormatGeneric(
            credentialFormats: Map<String, JsonElement>?,
        ): T {
            val json = Json {
                ignoreUnknownKeys = true
            }

            val anonCredsJson = credentialFormats?.get("indy")
                ?: throw IllegalArgumentException("Missing 'indy' credential format")

            return json.decodeFromJsonElement(anonCredsJson)
        }
    }
}
