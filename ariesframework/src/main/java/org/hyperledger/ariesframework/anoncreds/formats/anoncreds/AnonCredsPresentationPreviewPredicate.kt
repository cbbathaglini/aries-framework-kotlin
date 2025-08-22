package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsPredicateType
import org.hyperledger.ariesframework.proofs.models.PredicateType

@Serializable
data class AnonCredsPresentationPreviewPredicate(
    val name: String,
    @SerialName("credentialDefinitionId")
    val credentialDefinitionId: String,
    val predicate: PredicateType,
    val threshold: Long
)