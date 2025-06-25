package org.hyperledger.ariesframework.vc.model

import W3cCredentialSubject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
data class W3cJsonLdVerifiableCredential(
    override val context: List<JsonElement>,
    override val id: String? = null,
    override val type: List<String>,
    override val issuer: JsonElement,
    override val issuanceDate: String,
    override val credentialSubject: List<W3cCredentialSubject>,
    override val expirationDate: String? = null,
    override val credentialSchema: List<W3cCredentialSchema>? = null,
    override val credentialStatus: W3cCredentialStatus? = null,

    @SerialName("proof")
    val proofs: List<LinkedDataProofBase> // polymorphic base type

) : W3cCredential() {
    val proofTypes: List<String>
        get() = proofs.map { it.type }

    val dataIntegrityCryptosuites: List<String>
        get() = proofs.filterIsInstance<DataIntegrityProof>().map { it.cryptosuite }

    val encoded: Map<String, Any>
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

    fun toJsonString(): String {
        val linkedDataProofModule = SerializersModule {
            polymorphic(LinkedDataProofBase::class) {
                subclass(LinkedDataProof::class, LinkedDataProof.serializer())
                subclass(DataIntegrityProof::class, DataIntegrityProof.serializer())
            }
        }

        val json = Json {
            prettyPrint = true
            encodeDefaults = true
            serializersModule = linkedDataProofModule
            classDiscriminator = "type"
        }

        return json.encodeToString(W3cJsonLdVerifiableCredential.serializer(), this)
    }

    companion object {
        fun fromJson(json: String): W3cJsonLdVerifiableCredential {
            val module = SerializersModule {
                polymorphic(LinkedDataProofBase::class) {
                    subclass(LinkedDataProof::class, LinkedDataProof.serializer())
                    subclass(DataIntegrityProof::class, DataIntegrityProof.serializer())
                }
            }

            val jsonParser = Json {
                serializersModule = module
                ignoreUnknownKeys = true
                prettyPrint = true
            }

            return jsonParser.decodeFromString(W3cJsonLdVerifiableCredential.serializer(), json)
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
    override val type: String,
    val created: String,
    val proofPurpose: String,
    val verificationMethod: String,
    val jws: String
) : LinkedDataProofBase()

@Serializable
@SerialName("DataIntegrityProof")
data class DataIntegrityProof(
    override val type: String,
    val cryptosuite: String,
    val created: String,
    val proofPurpose: String,
    val verificationMethod: String,
    val proofValue: String
) : LinkedDataProofBase()