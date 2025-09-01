package org.hyperledger.ariesframework.proofs.handlers.v2

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.proofs.messages.v2.PresentationAckMessageV2
import org.hyperledger.ariesframework.proofs.v2.ProofServiceV2
import org.slf4j.LoggerFactory

class PresentationAckHandlerV2(val agent: Agent) : MessageHandler {
    private val logger = LoggerFactory.getLogger(PresentationAckHandlerV2::class.java)
    override val messageType = PresentationAckMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        logger.debug("Entering in PresentationAckHandlerV2")
        agent.proofService.processAck(messageContext)
        return null
    }
}
