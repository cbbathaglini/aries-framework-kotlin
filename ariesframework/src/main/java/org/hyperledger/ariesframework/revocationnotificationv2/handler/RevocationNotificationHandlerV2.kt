package org.hyperledger.ariesframework.revocationnotificationv2.handler

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.revocationnotificationv2.message.RevocationNotificationMessageV2

class RevocationNotificationHandlerV2(val agent: Agent) : MessageHandler {
    override val messageType = RevocationNotificationMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        agent.revocationNotificationServicev2.processRevocationNotification(messageContext)
        return null
    }
}
