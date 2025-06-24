package org.hyperledger.ariesframework.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AnyMapSerializer : KSerializer<Map<String, Any>> {
    private val delegate = MapSerializer(String.serializer(), AnyValueSerializer)
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: Map<String, Any>) = delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): Map<String, Any> = delegate.deserialize(decoder)
}