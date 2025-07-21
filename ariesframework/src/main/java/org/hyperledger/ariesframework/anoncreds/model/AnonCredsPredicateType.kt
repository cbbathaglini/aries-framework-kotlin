package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class AnonCredsPredicateType {
    @SerialName("<")
    LESS_THAN,

    @SerialName("<=")
    LESS_THAN_OR_EQUAL,

    @SerialName(">=")
    GREATER_THAN_OR_EQUAL,

    @SerialName(">")
    GREATER_THAN
}