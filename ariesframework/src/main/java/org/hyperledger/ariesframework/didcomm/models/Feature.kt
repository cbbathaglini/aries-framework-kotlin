package org.hyperledger.ariesframework.didcomm.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
open class Feature(
    open val id: String,

    @SerialName("feature-type")
    open val type: String,
) {

    /**
     * Combine this feature with another one, provided both are of the same type
     * and have the same id.
     *
     * @param feature object to combine with this one
     * @return a new object resulting from the combination of this and the feature
     */
    fun combine(feature: Feature): Feature {
        require(feature.id == this.id) { "Can only combine with a feature with the same id" }

        val json1 = Json.encodeToJsonElement(this) as JsonObject
        val json2 = Json.encodeToJsonElement(feature) as JsonObject
        val combinedJson = json1.toMutableMap()

        for ((key, value) in json2) {
            combinedJson[key] = when {
                value is JsonPrimitive && json1[key] is JsonPrimitive -> value
                value is JsonObject -> value // Handling nested JSON (can be enhanced)
                else -> value
            }
        }

        return Json.decodeFromJsonElement(JsonObject(combinedJson))
    }

    fun toJSON(): Map<String, Any?> {
        return Json.encodeToJsonElement(this).jsonObject.mapValues { it.value.toString() }
    }
}
