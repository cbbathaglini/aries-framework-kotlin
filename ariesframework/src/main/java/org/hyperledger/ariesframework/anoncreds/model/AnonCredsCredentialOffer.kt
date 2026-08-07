package org.hyperledger.ariesframework.anoncreds.model

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
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

            val gson = Gson()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(decodedString, type)

            val jsonStr = gson.toJson(map.get("key_correctness_proof"))

            // val c = jsonObject["c"].asString
            // val xzCap = jsonObject["xz_cap"].asString
            // val xrCap = jsonObject["xr_cap"].asJsonArray
            val jsonElement = JsonParser.parseString(map["key_correctness_proof"].toString()).asJsonObject
            val keyCorrectnessProof = gson.fromJson(jsonElement, KeyCorrectnessProof::class.java)

//            Log.i("MapResult", map.toString())
//            Log.i("key_correctness_proof", map.get("key_correctness_proof").toString())

            return AnonCredsCredentialOffer(
                schemaId = map.get("schema_id").toString(),
                credDefId = map.get("cred_def_id").toString(),
                nonce = map.get("nonce").toString(),
                keyCorrectnessProof = keyCorrectnessProof,
            )
        }

        private fun convertToValidJson(raw: String?): String {
            if (raw == null) return "{}"
            // Fix "=" to ":" and add quotes
            val fixed = raw
                .replace("=", "\":\"")
                .replace(", ", "\", \"")
                .replace("[[", "[[\"")
                .replace("], [", "\"], [\"")
                .replace("]]", "\"]]")
            return "{ \"$fixed\" }"
        }
    }
}
