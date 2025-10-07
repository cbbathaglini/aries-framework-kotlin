package org.hyperledger.ariesframework.credentials.v2.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.credentials.models.AcceptRequestOptionsV2
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.messages.IssueCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.RequestCredentialMessageV2
import org.hyperledger.ariesframework.error.CredoError
import org.slf4j.LoggerFactory

class RequestCredentialHandlerV2(val agent: Agent) : MessageHandler {

    private val logger = LoggerFactory.getLogger(RequestCredentialHandlerV2::class.java)
    override val messageType = RequestCredentialMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        logger.info("RequestCredentialHandlerV2 init")
        val credentialRecord = agent.credentialServiceV2.processRequest(messageContext)

        val shouldAutoRespond = agent.credentialServiceV2.shouldAutoRespondToRequest(
            credentialRecord = credentialRecord,
            messageContext = messageContext,
        )

        if (shouldAutoRespond) {
            val message = acceptRequest(credentialRecord)
            return OutboundMessage(message, messageContext.connection!!)
        }

        return null
    }

    private suspend fun acceptRequest(credentialRecord: CredentialExchangeRecord): IssueCredentialMessageV2 {
        logger.info("Automatically sending credential with autoAccept")

        val offerMessage = agent.credentialServiceV2.findOfferMessage(credentialRecord.id)
        if (offerMessage == null) {
            throw CredoError("Could not find offer message for credential record with id ${credentialRecord.id}")
        }

        val accept = AcceptRequestOptionsV2(
            credentialExchangeRecord = credentialRecord,
        )
        val (credentialExchange, message) = agent.credentialServiceV2.acceptRequest(accept)

        return message
    }
}
