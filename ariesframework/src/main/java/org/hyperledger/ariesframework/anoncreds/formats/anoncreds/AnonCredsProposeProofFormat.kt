package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedAttribute
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedPredicate
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval

@Serializable
data class AnonCredsProposeProofFormat(
    val name: String? = null,
    val version: String? = null,
    @SerialName("requested_attributes")
    val requestedAttributes: Map<String, AnonCredsRequestedAttribute>? = null,
    @SerialName("requested_predicates")
    val requestedPredicates: Map<String, AnonCredsRequestedPredicate>? = null,
    val attributes: List<AnonCredsPresentationPreviewAttribute>? = null,
    val predicates: List<AnonCredsPresentationPreviewPredicate>? = null,
    @SerialName("non_revoked")
    val nonRevokedInterval: AnonCredsNonRevokedInterval? = null,
)
