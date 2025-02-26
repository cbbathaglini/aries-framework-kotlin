package org.hyperledger.ariesframework.credentialsv2.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentialsv2.messages.RequestCredentialMessageV2
import org.hyperledger.ariesframework.didcomm.OutboundMessageContextUtil
import org.hyperledger.ariesframework.error.CredoError
import org.slf4j.LoggerFactory


class RequestCredentialHandlerV2(val agent: Agent) : MessageHandler {

    private val logger = LoggerFactory.getLogger(RequestCredentialHandlerV2::class.java)
    override val messageType =  RequestCredentialMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext) {
        val credentialRecord = agent.credentialServiceV2.processRequest(messageContext)

        val shouldAutoRespond = agent.credentialServiceV2.shouldAutoRespondToRequest(
            credentialRecord,
            messageContext.message
        )

        if (shouldAutoRespond) {
            acceptRequest(credentialRecord, messageContext)
        }
    }

    private suspend fun acceptRequest(
        credentialRecord: CredentialExchangeRecord,
        messageContext: InboundMessageContext
    ) {
        logger.info("Automatically sending credential with autoAccept")

        val offerMessage = agent.credentialServiceV2.findOfferMessage(
            credentialRecord.id
        ) ?: throw CredoError("Could not find offer message for credential record with id ${credentialRecord.id}")

        val message = agent.credentialServiceV2.acceptRequest(
            credentialRecord
        ).message

        val outboundMessageContextUtil = OutboundMessageContextUtil(agent)
        outboundMessageContextUtil.getOutboundMessageContext(
            messageContext.agentContext,
            connectionRecord = messageContext.connection,
            message = message,
            associatedRecord = credentialRecord,
            lastReceivedMessage = messageContext.message,
            lastSentMessage = offerMessage
        )
    }
}