package org.hyperledger.ariesframework.anoncreds.model

import android.util.Base64
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.agent.decorators.Attachment

@Serializable
data class AnonCredsCredentialOffer(
    @SerializedName("schema_id") @SerialName("schema_id")
    val schemaId: String,
    @SerializedName("cred_def_id") @SerialName("cred_def_id")
    val credDefId: String,
    val nonce: String,
    @SerializedName("key_correctness_proof") @SerialName("key_correctness_proof")
    val keyCorrectnessProof: KeyCorrectnessProof? = null,
) {

    fun toJsonString(): String = Json.encodeToString(this) // TODO

    override fun toString(): String {
        return "AnonCredsCredentialOffer(schemaId='$schemaId', credDefId='$credDefId', nonce='$nonce', keyCorrectnessProof=$keyCorrectnessProof)"
    }

    companion object {
        fun fromAttachment(attachment: Attachment): AnonCredsCredentialOffer {
            val dataDecoded = Base64.decode(attachment.data.base64, Base64.DEFAULT)
            val decodedString = String(dataDecoded, Charsets.UTF_8)

            return Json.decodeFromString(decodedString)
        }

    }
}
