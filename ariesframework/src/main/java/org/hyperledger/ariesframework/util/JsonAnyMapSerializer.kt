package org.hyperledger.ariesframework.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import org.hyperledger.ariesframework.util.JsonUtils.Companion.toKotlinValue

/** Stores native AnonCreds key material as JSON instead of contextual JVM objects. */
object JsonAnyMapSerializer : KSerializer<Map<String, Any?>> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(ConvertMapAnySerializer.mapAnyToJsonElement(value))
    }

    override fun deserialize(decoder: Decoder): Map<String, Any?> {
        require(decoder is JsonDecoder)
        val value = decoder.decodeJsonElement() as JsonObject
        return value.mapValues { (_, element) -> decodeValue(element) }
    }

    private fun decodeValue(element: JsonElement): Any? = when (element) {
        JsonNull -> null
        is JsonObject -> element.mapValues { (_, value) -> decodeValue(value) }
        is JsonArray -> element.map { decodeValue(it) }
        else -> element.toKotlinValue()
    }
}
