package org.hyperledger.ariesframework.proofs.v2.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.proofs.v2.messages.PresentationAckMessageV2
import org.hyperledger.ariesframework.util.LogUtil
import org.slf4j.LoggerFactory

class PresentationAckHandlerV2(val agent: Agent) : MessageHandler {
    override val messageType = PresentationAckMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        LogUtil.info(this) { "handler presentation ack" }
        agent.proofServiceV2.processAck(messageContext)
        return null
    }
}
