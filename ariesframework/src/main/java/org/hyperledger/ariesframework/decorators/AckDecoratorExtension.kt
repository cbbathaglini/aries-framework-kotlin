package org.hyperledger.ariesframework.decorators

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
open class AckDecoratorExtension(
    @SerialName("~please_ack")
    var pleaseAck: AckDecorator? = null
) : BaseMessage() {

    fun setPleaseAck(on: List<AckValues> = listOf(AckValues.Receipt)) {
        this.pleaseAck = AckDecorator(on)
    }

    fun getPleaseAck(): AckDecorator? {
        return this.pleaseAck
    }

    fun requiresAck(): Boolean {
        return this.pleaseAck != null
    }
}