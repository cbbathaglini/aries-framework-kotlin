package org.hyperledger.ariesframework.decorators

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AckValues {
    @SerialName("RECEIPT")
    Receipt,

    @SerialName("OUTCOME")
    Outcome,
}
