package org.hyperledger.ariesframework.vc.model

import W3cCredentialSubject
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class W3cUniffipattern(
    @SerialName("@context") @JsonProperty("@context")
    val context: List<JsonElement>,
    val id: String? = null,
    val type: List<String>,
    val issuer: JsonElement,
    val issuanceDate: String,
    val credentialSubject:  Map<String, String>,
    val expirationDate: String? = null,
    val credentialSchema: List<W3cCredentialSchema>? = null,
    val credentialStatus: W3cCredentialStatus? = null,

    @SerialName("proof")
    val proofs: List<LinkedDataProofBase> // polymorphic base type

)  {

}