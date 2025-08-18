package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

//@Serializable
//data class AnonCredsProofRequest(
//    val name: String,
//    val version: String,
//    val nonce: String,
//
//    @SerialName("requested_attributes")
//    val requestedAttributes: Map<String, AnonCredsRequestedAttribute>,
//    @SerialName("requested_predicates")
//    val requestedPredicates: Map<String, AnonCredsRequestedPredicate>,
//
//    @SerialName("non_revoked")
//    val nonRevoked: AnonCredsNonRevokedInterval? = null,
//    val ver: ProofRequestVersion? = null
//)
//
//enum class ProofRequestVersion(val value: String) {
//    V1_0("1.0"),
//    V2_0("2.0")
//}