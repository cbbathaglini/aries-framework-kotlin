package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialInfo

@Serializable
data class RequestedAttributeAnonCreds(
    // @SerialName("cred_id")
    val credentialId: String,
    val timestamp: Int? = null,
    val revealed: Boolean,
    @Transient
    var credentialInfo: AnonCredsCredentialInfo? = null,
    @Transient
    var revoked: Boolean? = null,
) {

    fun toJsonElement(): JsonElement =
        buildJsonObject {
            put("credentialId", JsonPrimitive(credentialId))
            put("revealed", JsonPrimitive(revealed))
            if (credentialInfo != null) {
                put("credentialInfo", credentialInfo!!.toJsonElement())
            }
            put("timestamp", JsonPrimitive(timestamp))
        }

    fun toJsonString(): String =
        Json.encodeToString(kotlinx.serialization.serializer<RequestedAttributeAnonCreds>(), this)
}

@Serializable
data class RequestedPredicateAnonCreds(
    // @SerialName("cred_id")
    val credentialId: String,
    val timestamp: Int? = null,
    @Transient
    var credentialInfo: AnonCredsCredentialInfo? = null,
    @Transient
    var revoked: Boolean? = null,
) {
    fun toJsonElement(): JsonElement =
        buildJsonObject {
            put("credentialId", JsonPrimitive(credentialId))
            put("revoked", JsonPrimitive(revoked))
            if (credentialInfo != null) {
                put("credentialInfo", credentialInfo!!.toJsonElement())
            }
            put("timestamp", JsonPrimitive(timestamp))
        }
}
