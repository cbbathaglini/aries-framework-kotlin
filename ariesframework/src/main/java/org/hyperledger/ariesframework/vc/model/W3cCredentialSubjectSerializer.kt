package org.hyperledger.ariesframework.vc.model

import W3cCredentialSubject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.json.*

object W3cCredentialSubjectSerializer : KSerializer<W3cCredentialSubject> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("W3cCredentialSubject")

    override fun deserialize(decoder: Decoder): W3cCredentialSubject {
        val input = decoder as? JsonDecoder
            ?: throw SerializationException("Expected JsonDecoder")

        val jsonObject = input.decodeJsonElement().jsonObject

        val id = jsonObject["id"]?.jsonPrimitive?.contentOrNull

        // Remove 'id' and take the rest as claims
        val claims = jsonObject
            .filterKeys { it != "id" }

        return W3cCredentialSubject(id = id, claims = claims)
    }

    override fun serialize(encoder: Encoder, value: W3cCredentialSubject) {
        val output = encoder as? JsonEncoder
            ?: throw SerializationException("Expected JsonEncoder")

        val content = mutableMapOf<String, JsonElement>()

        value.id?.let { content["id"] = JsonPrimitive(it) }
        value.claims?.let { content.putAll(it) }

        output.encodeJsonElement(JsonObject(content))
    }
}