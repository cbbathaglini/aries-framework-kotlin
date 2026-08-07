package org.hyperledger.ariesframework.agent.decorators

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

@Serializable
class JwsGeneralFormat(
    val header: Map<String, String>? = null,
    val signature: String,
    val protected: String,
) : Jws()

@Serializable
class JwsFlattenedFormat(val signatures: ArrayList<JwsGeneralFormat>) : Jws()

@Serializable(with = JwsSerializer::class)
abstract class Jws

@OptIn(ExperimentalSerializationApi::class)
object JwsSerializer : JsonContentPolymorphicSerializer<Jws>(Jws::class) {
    override fun selectDeserializer(element: JsonElement): KSerializer<out Jws> =
        when (element.jsonObject.size) {
            1 -> serializer<JwsFlattenedFormat>() // instead of JwsFlattenedFormat.serializer()
            else -> serializer<JwsGeneralFormat>() // instead of JwsGeneralFormat.serializer()
        }
}
