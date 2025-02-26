package org.hyperledger.ariesframework.credentialsv2.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentialsv2.messages.OfferCredentialMessageV2
import org.hyperledger.ariesframework.didcomm.OutboundMessageContextUtil

class OfferCredentialHandlerV2(val agent: Agent) : MessageHandler {

    override val messageType = OfferCredentialMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext) {
        val credentialRecord = agent.credentialServiceV2.processOffer(messageContext)

        val shouldAutoRespond = agent.credentialServiceV2.shouldAutoRespondToOffer(
            credentialRecord,
            messageContext.message
        )

        if (shouldAutoRespond) {
            acceptOffer(credentialRecord, messageContext)
        }
    }

    private suspend fun acceptOffer(
        credentialRecord: CredentialExchangeRecord,
        messageContext: InboundMessageContext<OfferCredentialMessageV2>
    ) {
        messageContext.agentContext.config.logger.info("Automatically sending request with autoAccept")

        val message = agent.credentialServiceV2.acceptOffer(
            messageContext.agentContext,
            credentialRecord
        ).message

        val outboundMessageContextUtil = OutboundMessageContextUtil(agent)
        outboundMessageContextUtil.getOutboundMessageContext(
            messageContext.agentContext,
            connectionRecord = messageContext.connection,
            message = message,
            associatedRecord = credentialRecord,
            lastReceivedMessage = messageContext.message
        )
    }
}