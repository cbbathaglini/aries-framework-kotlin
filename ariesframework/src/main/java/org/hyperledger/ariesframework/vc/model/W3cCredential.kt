package org.hyperledger.ariesframework.vc.model

import W3cCredentialSubject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.util.ConvertMapAnySerializer


@Serializable
abstract class W3cCredential {

    @SerialName("@context")
    abstract val context: List<JsonElement>

    abstract val id: String?
    abstract val type: List<String>
    abstract val issuer: JsonElement
    abstract val issuanceDate: String
    abstract val expirationDate: String?
    abstract val credentialSubject: List<W3cCredentialSubject>
    abstract val credentialSchema: List<W3cCredentialSchema>?
    abstract val credentialStatus: W3cCredentialStatus?

    val issuerId: String
        get() = when {
            issuer is JsonPrimitive && (issuer as JsonPrimitive).isString -> (issuer as JsonPrimitive).content
            issuer is JsonObject && (issuer as JsonObject)["id"] is JsonPrimitive -> (issuer as JsonObject)["id"]!!.jsonPrimitive.content
            else -> throw IllegalStateException("issuer must be a string or object with 'id'")
        }

    val credentialSchemaIds: List<String>
        get() = credentialSchema?.map { it.id } ?: emptyList()

    val credentialSubjectIds: List<String>
        get() = credentialSubject.mapNotNull { it.id }

    val contexts: List<Any>
        get() = context // ou `context.map(JsonElement::toString)` se desejar

    companion object {
        const val CREDENTIALS_CONTEXT_V1_URL = "https://www.w3.org/2018/credentials/v1"

        fun fromJson(jsonStr: Map<String, Any?>): W3cCredential {
            val jsonElement = ConvertMapAnySerializer.anyToJsonElement(jsonStr)
            return Json.decodeFromJsonElement(W3cJsonLdVerifiableCredential.serializer(), jsonElement)
        }
    }
}

//
//open class W3cCredential(
//    @SerialName("@context")
//    open val context: List<JsonElement> = listOf(JsonPrimitive(CREDENTIALS_CONTEXT_V1_URL)), // String ou JsonObject
//
//    open val id: String? = null,
//
//    open val type: List<String> = listOf("VerifiableCredential"),
//
//    open val issuer: JsonElement,
//
//    open val issuanceDate: String,
//
//    open val expirationDate: String? = null,
//
//    open val credentialSubject: List<W3cCredentialSubject>,
//
//    open val credentialSchema: List<W3cCredentialSchema>? = null,
//
//    open val credentialStatus: W3cCredentialStatus? = null
//) : W3cVerifiableCredential{
//
//    companion object {
//        private const val CREDENTIALS_CONTEXT_V1_URL = "https://www.w3.org/2018/credentials/v1"
//
////        fun fromJson(jsonStr: Map<String, Any?>): W3cCredential {
////            val jsonElement: JsonElement = ConvertMapAnySerializer.anyToJsonElement(jsonStr)
////            return Json.decodeFromJsonElement(W3cCredential.serializer(), jsonElement)
////        }
//    }
//
//    val issuerId: String
//        get() = when {
//            issuer is JsonPrimitive && (issuer as JsonPrimitive).isString -> (issuer as JsonPrimitive).content
//            issuer is JsonObject && (issuer as JsonObject)["id"] is JsonPrimitive -> (issuer as JsonObject)["id"]!!.jsonPrimitive.content
//            else -> throw IllegalStateException("issuer must be a string or object with 'id'")
//        }
//
//    val credentialSchemaIds: List<String>
//        get() = credentialSchema?.map { it.id } ?: emptyList()
//
//    val credentialSubjectIds: List<String>
//        get() = credentialSubject.mapNotNull { it.id }
//
//    val contexts: List<Any>
//        get() = context
//
//}