package org.hyperledger.ariesframework.proofs.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.proofs.messages.PresentationMessage
import org.hyperledger.ariesframework.proofs.messages.PresentationMessageV2
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof

class PresentationHandlerV2(val agent: Agent) : MessageHandler {
    override val messageType = PresentationMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val presentationRecord = agent.proofServiceV2.processPresentation(messageContext)

        if (presentationRecord.autoAcceptProof == AutoAcceptProof.Always ||
            agent.agentConfig.autoAcceptProof == AutoAcceptProof.Always
        ) {
            val (message, _) = agent.proofServiceV2.createAck(presentationRecord)
            return OutboundMessage(message, messageContext.connection!!)
        }

        return null
    }
}
