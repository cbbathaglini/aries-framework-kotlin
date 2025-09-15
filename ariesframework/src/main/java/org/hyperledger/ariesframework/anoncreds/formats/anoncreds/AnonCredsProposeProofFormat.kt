package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval

@Serializable
data class AnonCredsProposeProofFormat(
    val name: String? = null,
    val version: String? = null,
    val attributes: List<AnonCredsPresentationPreviewAttribute>? = null,
    val predicates: List<AnonCredsPresentationPreviewPredicate>? = null,
    @SerialName("nonRevokedInterval")
    val nonRevokedInterval: AnonCredsNonRevokedInterval? = null,
)
