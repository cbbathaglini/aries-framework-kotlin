package org.hyperledger.ariesframework.vc.model

import W3cCredentialSubject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlinx.serialization.json.*

@Serializable
data class W3cCredential(
    @SerialName("@context")
    val context: List<JsonElement>,
    val id: String? = null,
    val type: List<String>,
    val issuer: JsonElement,
    val issuanceDate: String,
    val credentialSubject: List<W3cCredentialSubject>,
    val expirationDate: String? = null,
    val credentialSchema: List<W3cCredentialSchema>? = emptyList(),
    val credentialStatus: W3cCredentialStatus?  = null,


    @SerialName("proof")
    //val proofs: List<LinkedDataProofBase>? = emptyList(), // add by me
    val proofs: List<JsonElement>? = emptyList(), // add by me

) {

    fun toJson(): String = Json {
        prettyPrint = true
        encodeDefaults = true
    }.encodeToString(serializer<W3cCredential>(), this)

    companion object {

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        fun fromJsonOb(jsonString: String): W3cCredential {
            val element = json.parseToJsonElement(jsonString)
            val obj = element.jsonObject
            val normalized = normalizeIncomingW3cPayload(obj)
            return json.decodeFromJsonElement(serializer(), normalized)
        }

//        private val json = Json {
//            ignoreUnknownKeys = true
//            isLenient = true
//        }

        fun fromJson(jsonString: String): W3cCredential {
            return json.decodeFromString(serializer(), jsonString)
        }


        fun normalizeIncomingW3cPayload(root: JsonObject): JsonObject {
            // 1) Unwrap: { format: "...", credential: {...} }
            val vcObj: JsonObject = when (val cred = root["credential"]) {
                is JsonObject -> cred
                else -> root
            }

            fun asArray(el: JsonElement?): JsonArray? = when (el) {
                null -> null
                is JsonArray -> el
                is JsonObject -> JsonArray(listOf(el))
                is JsonPrimitive -> JsonArray(listOf(el))
                else -> null
            }

            fun contextAsArray(el: JsonElement?): JsonArray? = when (el) {
                null -> null
                is JsonArray -> el
                is JsonPrimitive -> JsonArray(listOf(el))
                is JsonObject -> JsonArray(listOf(el))
                else -> null
            }

            val mutable = vcObj.toMutableMap()

            // 2) @context pode vir string -> [string]
            contextAsArray(vcObj["@context"])?.let { mutable["@context"] = it }

            // 3) type pode vir string -> [string]
            asArray(vcObj["type"])?.let { mutable["type"] = it }

            // 4) credentialSubject pode vir objeto -> [obj]
            val subjectArray = asArray(vcObj["credentialSubject"])
            if (subjectArray != null) {
                val subjects = subjectArray.mapNotNull { it as? JsonObject }

                val liftedProofs = mutableListOf<JsonObject>()
                val cleanedSubjects = subjects.map { subj ->
                    val subjMap = subj.toMutableMap()

                    // ✅ id -> @id (pra bater com @SerialName("@id"))
                    if (subjMap.containsKey("id") && !subjMap.containsKey("@id")) {
                        subjMap["@id"] = subjMap.remove("id")!!
                    }

                    // ✅ se vier proof dentro do subject, sobe pro topo
                    val subjProof = subj["proof"]
                    val subjProofArray = asArray(subjProof)?.mapNotNull { it as? JsonObject } ?: emptyList()

                    if (subjProofArray.isNotEmpty()) {
                        liftedProofs.addAll(subjProofArray)
                        subjMap.remove("proof")
                    }

                    JsonObject(subjMap)
                }

                mutable["credentialSubject"] = JsonArray(cleanedSubjects)

                // 5) proof pode vir no topo e/ou nos subjects -> junta tudo em array
                val topProof = vcObj["proof"]
                val topProofArray = asArray(topProof)?.mapNotNull { it as? JsonObject } ?: emptyList()

                val finalProofs = (topProofArray + liftedProofs).distinctBy { it.toString() }
                if (finalProofs.isNotEmpty()) {
                    mutable["proof"] = JsonArray(finalProofs)
                }
            }

            // 6) credentialSchema pode vir objeto -> [obj]
            asArray(vcObj["credentialSchema"])?.let { mutable["credentialSchema"] = it }

            return JsonObject(mutable)
        }

//        fun normalizeW3cVcJson(obj: JsonObject): JsonObject {
//            fun asArray(el: JsonElement?): JsonArray? = when (el) {
//                null -> null
//                is JsonArray -> el
//                is JsonObject -> JsonArray(listOf(el))
//                is JsonPrimitive -> JsonArray(listOf(el)) // útil p/ alguns campos que aceitam string
//                else -> null
//            }
//
//            fun contextAsArray(el: JsonElement?): JsonArray? = when (el) {
//                null -> null
//                is JsonArray -> el
//                is JsonPrimitive -> JsonArray(listOf(el)) // "@context": "https://..."
//                is JsonObject -> JsonArray(listOf(el))
//                else -> null
//            }
//
//            val mutable = obj.toMutableMap()
//
//            // ✅ credentialSubject: objeto -> [obj]
//            asArray(obj["credentialSubject"])?.let { mutable["credentialSubject"] = it }
//
//            // ✅ proof: objeto -> [obj]
//            asArray(obj["proof"])?.let { mutable["proof"] = it }
//
//            // ✅ credentialSchema às vezes vem como objeto também
//            asArray(obj["credentialSchema"])?.let { mutable["credentialSchema"] = it }
//
//            // ✅ @context: string -> [string]
//            contextAsArray(obj["@context"])?.let { mutable["@context"] = it }
//
//            // ✅ type às vezes vem como string
//            asArray(obj["type"])?.let { mutable["type"] = it }
//
//            return JsonObject(mutable)
//        }
    }

}
