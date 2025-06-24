package org.hyperledger.ariesframework.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

object AnyValueSerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("This serializer can be used only with JSON")

        val jsonElement = when (value) {
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Map<*, *> -> jsonEncoder.json.encodeToJsonElement(MapSerializer(String.serializer(), this), value as Map<String, Any>)
            is List<*> -> jsonEncoder.json.encodeToJsonElement(ListSerializer(this), value as List<Any>)
            else -> throw SerializationException("Unsupported type: ${value::class}")
        }

        jsonEncoder.encodeJsonElement(jsonElement)
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("This serializer can be used only with JSON")

        val element = jsonDecoder.decodeJsonElement()
        return decodeJsonElement(element)
    }

    private fun decodeJsonElement(element: JsonElement): Any {
        return when (element) {
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.intOrNull != null -> element.int
                element.longOrNull != null -> element.long
                element.doubleOrNull != null -> element.double
                else -> element.content
            }
            is JsonObject -> element.mapValues { decodeJsonElement(it.value) }
            is JsonArray -> element.map { decodeJsonElement(it) }
        }
    }
}