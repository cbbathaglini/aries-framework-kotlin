package org.hyperledger.ariesframework.connection.models.didauth

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

@Serializable(with = AuthenticationSerializer::class)
abstract class Authentication

// object AuthenticationSerializer : JsonContentPolymorphicSerializer<Authentication>(Authentication::class) {
//    override fun selectDeserializer(element: JsonElement) = when (element.jsonObject.size) {
//        2 -> ReferencedAuthentication.serializer()
//        else -> EmbeddedAuthentication.serializer()
//    }
// }

@OptIn(ExperimentalSerializationApi::class)
object AuthenticationSerializer :
    JsonContentPolymorphicSerializer<Authentication>(Authentication::class) {

    override fun selectDeserializer(element: JsonElement): KSerializer<out Authentication> =
        when (element.jsonObject.size) {
            2 -> serializer<ReferencedAuthentication>() // antes: ReferencedAuthentication.serializer()
            else -> serializer<EmbeddedAuthentication>() // antes: EmbeddedAuthentication.serializer()
        }
}
