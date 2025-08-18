package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsPredicateType

@Serializable
data class AnonCredsPresentationPreviewPredicate(
    val name: String,
    @SerialName("credentialDefinitionId")
    val credentialDefinitionId: String,
    val predicate: AnonCredsPredicateType,
    val threshold: Long
)