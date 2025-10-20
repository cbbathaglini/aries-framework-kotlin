package org.hyperledger.ariesframework.proofs.v2.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.proofs.v2.messages.PresentationMessageV2
import org.slf4j.LoggerFactory

class PresentationHandlerV2(val agent: Agent) : MessageHandler {
    private val logger = LoggerFactory.getLogger(PresentationAckHandlerV2::class.java)

    override val messageType = PresentationMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        logger.debug("Entering in PresentationHandlerV2")
        val presentationRecord = agent.proofServiceV2.processPresentation(messageContext)

        logger.info("prseentarecord: ${presentationRecord.isVerified} $presentationRecord")
        if (presentationRecord.autoAcceptProof == AutoAcceptProof.Always ||
            agent.agentConfig.autoAcceptProof == AutoAcceptProof.Always
        ) {
            val (message, _) = agent.proofServiceV2.createAck(presentationRecord)
            return OutboundMessage(message, messageContext.connection!!)
        }

        return null
    }
}
