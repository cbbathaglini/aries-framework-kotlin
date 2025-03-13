package org.hyperledger.ariesframework.proofs.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.proofs.messages.PresentationAckMessage
import org.hyperledger.ariesframework.proofs.messages.PresentationAckMessageV2

class PresentationAckHandlerV2(val agent: Agent) : MessageHandler {
    override val messageType = PresentationAckMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        agent.proofServiceV2.processAck(messageContext)
        return null
    }
}
