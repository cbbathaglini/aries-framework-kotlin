package org.hyperledger.ariesframework.anoncreds.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.issuer.AnonCredsCredentialValue

import kotlinx.serialization.SerialName


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
    @Contextual
    val signature: Any,

    @SerialName("signature_correctness_proof") @SerializedName("signature_correctness_proof")
    @Contextual
    val signatureCorrectnessProof: Any,

    @SerialName("rev_reg") @SerializedName("rev_reg")
    @Contextual
    val revReg: Any? = null,

    @SerialName("witness")
    @Contextual
    val witness: Any? = null
){
    override fun toString(): String {
        return "AnonCredsCredential(schemaId='$schemaId', credDefId='$credDefId', revRegId=$revRegId, values=$values, signature=$signature, signatureCorrectnessProof=$signatureCorrectnessProof, revReg=$revReg, witness=$witness)"
    }
}