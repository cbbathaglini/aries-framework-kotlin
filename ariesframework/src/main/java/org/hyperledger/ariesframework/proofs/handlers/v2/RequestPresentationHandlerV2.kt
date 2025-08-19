package org.hyperledger.ariesframework.proofs.handlers.v2

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.proofs.messages.v2.RequestPresentationMessageV2
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

class RequestPresentationHandlerV2(val agent: Agent) : MessageHandler {
    override val messageType = RequestPresentationMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val proofRecord = agent.proofServiceV2.processRequest(messageContext)

        if (proofRecord.autoAcceptProof == AutoAcceptProof.Always ||
            agent.agentConfig.autoAcceptProof == AutoAcceptProof.Always
        ) {
            return createPresentation(proofRecord, messageContext)
        }

        return null
    }

    suspend fun createPresentation(record: ProofExchangeRecord, messageContext: InboundMessageContext): OutboundMessage? {
        val retrievedCredentials = agent.proofs.getRequestedCredentialsForProofRequest(record.id)
        val requestedCredentials = agent.proofService.autoSelectCredentialsForProofRequest(retrievedCredentials)

        val (message, _) = agent.proofService.createPresentation(record, requestedCredentials)
        return OutboundMessage(message, messageContext.connection!!)
    }
}
