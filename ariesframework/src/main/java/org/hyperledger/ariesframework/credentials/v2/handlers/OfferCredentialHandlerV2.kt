package org.hyperledger.ariesframework.credentials.v2.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.credentials.v1.AcceptOfferOptions
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential
import org.slf4j.LoggerFactory

class OfferCredentialHandlerV2(val agent: Agent) : MessageHandler {

    private val logger = LoggerFactory.getLogger(OfferCredentialHandlerV2::class.java)
    override val messageType = OfferCredentialMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        logger.info("[IDD][ORDER] OfferCredentialHandlerV2")
        logger.info("[IDD][ORDER] message: ${messageContext.message}")
        logger.info("[IDD][ORDER] plaintextMessage: ${messageContext.plaintextMessage}")
        val credentialRecord = agent.credentialServiceV2.processOfferCredentialMessageV2(messageContext)

        logger.info("[IDD][ORDER] OfferCredentialHandlerV2 credentialRecord ${credentialRecord}")
        if (credentialRecord.autoAcceptCredential == AutoAcceptCredential.Always ||
            agent.agentConfig.autoAcceptCredential == AutoAcceptCredential.Always
        ) {
            logger.info("[IDD][ORDER][handle] OfferCredentialHandlerV2 - createRequest ")
            val message = agent.credentialServiceV2.createRequestCredentialMessage(AcceptOfferOptions(credentialRecord.id))
            return OutboundMessage(message, messageContext.connection!!)
        }

        logger.info("[IDD][ORDER][handle] OfferCredentialHandlerV2 - returning null")
        return null
    }

}