package org.hyperledger.ariesframework.decorators

import kotlinx.serialization.Serializable

@Serializable
data class AckDecorator(
    val on: List<AckValues> = listOf(AckValues.Receipt)
) {
    constructor(options: Map<String, List<AckValues>>) : this(
        on = options["on"] ?: listOf(AckValues.Receipt)
    )
}