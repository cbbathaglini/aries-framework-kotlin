package org.hyperledger.ariesframework.revocationnotificationv2.handler

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.revocationnotificationv2.message.RevocationNotificationMessageV2
import org.hyperledger.ariesframework.util.LogUtil
import org.slf4j.LoggerFactory

class RevocationNotificationHandlerV2(val agent: Agent) : MessageHandler {
    override val messageType = RevocationNotificationMessageV2.type
    private val logger = LoggerFactory.getLogger(RevocationNotificationHandlerV2::class.java)

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        LogUtil.info(this) { "handle revocation notification -  ${messageContext.plaintextMessage}" }

        agent.revocationNotificationServicev2.processRevocationNotification(messageContext)
        return null
    }
}
