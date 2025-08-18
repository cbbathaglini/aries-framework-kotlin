package org.hyperledger.ariesframework.anoncreds.model

import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval

data class ReferentItem(
    val type: Type,
    val referent: String,
    val selectedCredential: Any, // AnonCredsRequestedAttributeMatch | AnonCredsRequestedPredicateMatch
    val nonRevoked: AnonCredsNonRevokedInterval?
) {
    enum class Type { ATTRIBUTES, PREDICATES }
}