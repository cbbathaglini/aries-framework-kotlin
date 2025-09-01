package org.hyperledger.ariesframework.credentials.v2.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.credentials.models.AcceptCredentialProposalOptions
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2
import org.slf4j.LoggerFactory

class ProposeCredentialHandlerV2(val agent: Agent) : MessageHandler {

    private val logger = LoggerFactory.getLogger(ProposeCredentialHandlerV2::class.java)
    override val messageType = OfferCredentialMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        logger.debug("ProposeCredentialHandlerV2 init")
        val credentialRecord = agent.credentialServiceV2.processProposal(messageContext)

        val shouldAutoRespond = agent.credentialServiceV2.shouldAutoRespondToProposal(
            credentialRecord = credentialRecord,
            messageContext = messageContext,
        )

        if (shouldAutoRespond) {
            return acceptProposal(credentialRecord, messageContext)
        }

        return null
    }

    suspend fun acceptProposal(
        credentialRecord: CredentialExchangeRecord,
        messageContext: InboundMessageContext,
    ): OutboundMessage {
        logger.info("Automatically sending offer with autoAccept")

        val connection = messageContext.connection
        if (connection == null) {
            logger.error("No connection on the messageContext, aborting auto accept")
            throw IllegalStateException("Missing connection for auto-accept proposal")
        }

        val (message) = agent.credentialServiceV2.acceptProposal(
            AcceptCredentialProposalOptions(
                credentialExchangeRecord = credentialRecord,
            ),
        )

        return OutboundMessage(
            payload = message,
            connection = connection,
        )
    }
}
