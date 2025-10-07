package org.hyperledger.ariesframework.revocationnotificationv2.handler

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.revocationnotificationv2.message.RevocationNotificationMessageV2
import org.slf4j.LoggerFactory

class RevocationNotificationHandlerV2(val agent: Agent) : MessageHandler {
    override val messageType = RevocationNotificationMessageV2.type
    private val logger = LoggerFactory.getLogger(RevocationNotificationHandlerV2::class.java)

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        logger.info("message: ${messageContext.message}")
        logger.info("plaintextMessage: ${messageContext.plaintextMessage}")

//        val revocationMessage = messageContext.message as? RevocationNotificationMessageV2
//            ?: throw CredoError("Invalid message type: Expected RevocationNotificationMessageV2")
//        logger.info("revocationMessage: ${revocationMessage.toString()}")

        agent.revocationNotificationServicev2.processRevocationNotification(messageContext)
        return null
    }
}
