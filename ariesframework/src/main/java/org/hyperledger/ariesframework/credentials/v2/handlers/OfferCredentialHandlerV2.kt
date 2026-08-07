package org.hyperledger.ariesframework.credentials.v2.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.credentials.models.AcceptCredentialOfferOptionsV2
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.RequestCredentialMessageV2
import org.hyperledger.ariesframework.util.LogUtil
import org.slf4j.LoggerFactory

class OfferCredentialHandlerV2(val agent: Agent) : MessageHandler {

    private val logger = LoggerFactory.getLogger(OfferCredentialHandlerV2::class.java)
    override val messageType = OfferCredentialMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        LogUtil.info(this) { "issue credential - offer step" }

        val credentialRecord = agent.credentialServiceV2.processOffer(messageContext)

        val shouldAutoRespond = agent.credentialServiceV2.shouldAutoRespondToOffer(
            credentialRecord = credentialRecord,
            messageContext = messageContext,
        )

        if (shouldAutoRespond) {
            val message = this.acceptOffer(credentialRecord)
            return OutboundMessage(message, messageContext.connection!!)
        }

        return null
    }

    private suspend fun acceptOffer(credentialRecord: CredentialExchangeRecord): RequestCredentialMessageV2 {
        // logger.info("Automatically sending request with autoAccept")

        val acceptCredentialOfferOptions = AcceptCredentialOfferOptionsV2(
            credentialExchangeRecord = credentialRecord,
        )
        val (credentialExchange, requestCredentialMessageV2) = agent.credentialServiceV2.acceptOffer(acceptCredentialOfferOptions)
        // logger.info("requestCredentialMessageV2 =>> $requestCredentialMessageV2")

        return requestCredentialMessageV2
    }
}
