package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsPresentationPreviewPredicate(
    val name: String,
    @SerialName("credentialDefinitionId")
    val credentialDefinitionId: String? = null,
    @SerialName("p_type")
    val predicateType: String,
    @SerialName("p_value")
    val threshold: Long,
)
