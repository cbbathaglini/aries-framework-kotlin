package org.hyperledger.ariesframework.anoncreds.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.issuer.AnonCredsCredentialValue

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement


@Serializable
data class AnonCredsCredential(
    @SerialName("schema_id") @SerializedName("schema_id")
    val schemaId: String,

    @SerialName("cred_def_id") @SerializedName("cred_def_id")
    val credDefId: String,

    @SerialName("rev_reg_id") @SerializedName("rev_reg_id")
    val revRegId: String? = null,

    @SerialName("values")
    val values: Map<String, AnonCredsCredentialValue>,

    @SerialName("signature")
    val signature: JsonElement,

    @SerialName("signature_correctness_proof") @SerializedName("signature_correctness_proof")
    val signatureCorrectnessProof: JsonElement,

    @SerialName("rev_reg") @SerializedName("rev_reg")
    val revReg: JsonElement? = null,

    @SerialName("witness")
    val witness: JsonElement? = null
){
    override fun toString(): String {
        return "AnonCredsCredential(schemaId='$schemaId', credDefId='$credDefId', revRegId=$revRegId, values=$values, signature=$signature, signatureCorrectnessProof=$signatureCorrectnessProof, revReg=$revReg, witness=$witness)"
    }
}