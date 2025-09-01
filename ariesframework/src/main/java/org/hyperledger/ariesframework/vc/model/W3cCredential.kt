package org.hyperledger.ariesframework.vc.model

import W3cCredentialSubject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class W3cCredential (
    @SerialName("@context")
    val context: List<JsonElement>,
    val id: String?,
    val type: List<String>,
    val issuer: JsonElement,
    val issuanceDate: String,
    val credentialSubject: List<W3cCredentialSubject>,
    val expirationDate: String? = null,
    val credentialSchema: List<W3cCredentialSchema>? = emptyList(),
    val credentialStatus: W3cCredentialStatus?,

    @SerialName("proof")
    val proofs: List<LinkedDataProofBase>?  = emptyList() // add by me

) {

    fun toJson(): String = Json {
        prettyPrint = true
        encodeDefaults = true
    }.encodeToString(W3cCredential.serializer(), this)

    companion object{
//        private val json = Json {
//            prettyPrint = true
//            ignoreUnknownKeys = true
//            isLenient = true
//        }
//
//        fun toJson(credential: W3cCredential): String {
//            return json.encodeToString(W3cCredential.serializer(), credential)
//        }
//
//        fun toJson(credentials: List<W3cCredential>): String {
//            return json.encodeToString(ListSerializer(W3cCredential.serializer()), credentials)
//        }
//
//        fun toJsonElement(credential: W3cCredential): JsonElement {
//            return json.encodeToJsonElement(W3cCredential.serializer(), credential)
//        }
    }
}

//abstract class W3cCredential {
//
//    @SerialName("@context")
//    open var context: List<JsonElement> = emptyList()
//
//    open var id: String? = null
//    open lateinit var type: List<String>
//    open lateinit var  issuer: JsonElement
//    open lateinit var  issuanceDate: String
//    open var  expirationDate: String? = null
//    open lateinit var  credentialSubject: List<W3cCredentialSubject>
//    open var credentialSchema: List<W3cCredentialSchema>? = emptyList()
//    open var credentialStatus: W3cCredentialStatus? = null
//
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
//        get() = context // ou `context.map(JsonElement::toString)` se desejar
//
//}
