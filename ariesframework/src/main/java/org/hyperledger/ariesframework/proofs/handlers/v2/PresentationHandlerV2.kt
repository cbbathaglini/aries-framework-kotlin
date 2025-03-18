package org.hyperledger.ariesframework.proofs.handlers.v2

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.proofs.messages.v2.PresentationMessageV2
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof

class PresentationHandlerV2(val agent: Agent) : MessageHandler {
    override val messageType = PresentationMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val presentationRecord = agent.proofService.processPresentation(messageContext)

        if (presentationRecord.autoAcceptProof == AutoAcceptProof.Always ||
            agent.agentConfig.autoAcceptProof == AutoAcceptProof.Always
        ) {
            val (message, _) = agent.proofService.createAck(presentationRecord)
            return OutboundMessage(message, messageContext.connection!!)
        }

        return null
    }
}
