package org.hyperledger.ariesframework.vc.model

import W3cCredentialSubject
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.serializer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema

@Serializable
data class W3cJsonLdVerifiableCredential(
    @JsonProperty("@context") @SerialName("@context")
    var context: List<JsonElement>,
    var id: String? = null,
    var type: List<String>,
    var issuer: JsonElement,
    var issuanceDate: String,
    var credentialSubject: List<W3cCredentialSubject>,
    var expirationDate: String? = null,
    var credentialSchema: List<W3cCredentialSchema>? = null,
    var credentialStatus: W3cCredentialStatus? = null,

    @SerialName("proof")
    val proofs: List<LinkedDataProofBase>?  = emptyList(),// polymorphic base type

) { //: W3cCredential {

    fun toJson(): String = Json {
        prettyPrint = true
        encodeDefaults = true
    }.encodeToString(serializer<W3cJsonLdVerifiableCredential>(), this)

    val encoded: Map<String, Any?>
        get() = mapOf( // simulate JSON for now; use kotlinx.serialization to generate full JSON
            "@context" to context,
            "type" to type,
            "issuer" to issuer,
            "issuanceDate" to issuanceDate,
            "credentialSubject" to credentialSubject,
            "proof" to proofs
        )

    val claimFormat: String
        get() = "ldp_vc"


    companion object {

        fun fromJson(json: String): W3cJsonLdVerifiableCredential {
            val module = SerializersModule {
                polymorphic(LinkedDataProofBase::class) {
                    subclass(LinkedDataProof::class, serializer<LinkedDataProof>())
                    subclass(DataIntegrityProof::class, serializer<DataIntegrityProof>())
                }
            }

            val jsonParser = Json {
                serializersModule = module
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true

            }

            return jsonParser.decodeFromString(serializer<W3cJsonLdVerifiableCredential>(), json)
        }
    }
}

// Base sealed class for proofs
@Serializable
sealed class LinkedDataProofBase {
    abstract val type: String
}

@Serializable
@SerialName("LinkedDataProof")
data class LinkedDataProof(
    val created: String,
    val proofPurpose: String,
    val verificationMethod: String,
    val jws: String? = null
) : LinkedDataProofBase(){
    override val type: String
        get() = "LinkedDataProof"
}

@Serializable
@SerialName("DataIntegrityProof")
data class DataIntegrityProof(
    val cryptosuite: String,
    val created: String? = null,
    val proofPurpose: String,
    val verificationMethod: String,
    val proofValue: String? = null
) : LinkedDataProofBase(){
    override val type: String
        get() = "DataIntegrityProof"
}