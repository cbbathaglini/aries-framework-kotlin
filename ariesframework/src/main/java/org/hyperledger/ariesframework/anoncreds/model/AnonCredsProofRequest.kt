package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval

@Serializable
data class AnonCredsProofRequest(
    val name: String,
    val version: String,
    val nonce: String,
    @SerialName("requested_attributes")
    val requestedAttributes: Map<String, AnonCredsRequestedAttribute>,
    @SerialName("requested_predicates")
    val requestedPredicates: Map<String, AnonCredsRequestedPredicate>,
    @SerialName("non_revoked")
    val nonRevoked: AnonCredsNonRevokedInterval? = null,
    val ver: String? = null, // can be "1.0" or "2.0"
)

@Serializable
data class AnonCredsRequestedAttribute(
    val name: String? = null,
    val names: List<String>? = null,
    val restrictions: List<AnonCredsProofRequestRestriction>? = null,
    @SerialName("non_revoked")
    val nonRevoked: AnonCredsNonRevokedInterval? = null,
)

@Serializable
data class AnonCredsRequestedPredicate(
    val name: String,
    @SerialName("p_type")
    val pType: AnonCredsPredicateType,
    @SerialName("p_value")
    val pValue: Int,
    val restrictions: List<AnonCredsProofRequestRestriction>? = null,
    @SerialName("non_revoked")
    val nonRevoked: AnonCredsNonRevokedInterval? = null,
)
