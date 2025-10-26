package org.hyperledger.ariesframework.proofs.v2.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.proofs.models.AcceptProofRequestOptions
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.proofs.models.RequestedCredentialsAnoncreds
import org.hyperledger.ariesframework.proofs.models.RetrievedCredentialsAnonCreds
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.proofs.v2.ProofUtils
import org.hyperledger.ariesframework.proofs.v2.messages.RequestPresentationMessageV2
import org.slf4j.LoggerFactory

class RequestPresentationHandlerV2(val agent: Agent) : MessageHandler {
    override val messageType = RequestPresentationMessageV2.type
    private val logger = LoggerFactory.getLogger(RequestPresentationHandlerV2::class.java)

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        logger.info("Entering in RequestPresentationHandlerV2")
        val proofRecord = agent.proofServiceV2.processRequest(messageContext)

        if (proofRecord.autoAcceptProof == AutoAcceptProof.Always ||
            agent.agentConfig.autoAcceptProof == AutoAcceptProof.Always
        ) {
            return createPresentation(proofRecord, messageContext)
        }

        return null
    }

    suspend fun createPresentation(record: ProofExchangeRecord, messageContext: InboundMessageContext): OutboundMessage? {
        val retrievedCredentials: RetrievedCredentialsAnonCreds =
            ProofUtils.getRequestedCredentialsForProofRequest(
                proofRecordId = record.id,
                agent = agent,
            )
        val requestedCredentials: RequestedCredentialsAnoncreds = agent.proofServiceV2.autoSelectCredentialsForProofRequest(retrievedCredentials)

        val params = AcceptProofRequestOptions(
            proofRecord = record,
            proofFormats = record.formats!!,
            requestedCredentials = requestedCredentials.toMap(),
        )
        val (message, _) = agent.proofServiceV2.acceptRequest(params)
        return OutboundMessage(message, messageContext.connection!!)
    }
}
