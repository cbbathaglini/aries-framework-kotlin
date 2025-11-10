package org.hyperledger.ariesframework.proofs.v2.messages

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.AckStatus
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator

@Serializable
class PresentationAckMessageV2(
    val status: AckStatus,
) : AgentMessage(generateId(), type) {
    constructor(threadId: String, status: AckStatus) : this(status) {
        thread = ThreadDecorator(threadId)
    }
    companion object {
        const val type = "https://didcomm.org/present-proof/2.0/ack"
    }

    override fun requestResponse(): Boolean {
        return false
    }
}
