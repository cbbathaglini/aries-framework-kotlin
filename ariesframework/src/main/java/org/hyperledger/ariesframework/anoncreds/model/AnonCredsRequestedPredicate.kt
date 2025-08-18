package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval

@Serializable
data class AnonCredsRequestedPredicate(
    val name: String,
    @SerialName("p_type")
    val pType: AnonCredsPredicateType,
    @SerialName("p_value")
    val pValue: Long,
    val restrictions: List<AnonCredsProofRequestRestriction>? = null,
    @SerialName("non_revoked")
    val nonRevoked: AnonCredsNonRevokedInterval? = null
)
