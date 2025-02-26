package org.hyperledger.ariesframework.revocationnotification.handler

import RevocationNotificationService
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.revocationnotification.message.RevocationNotificationMessageV1

class RevocationNotificationHandlerV1(
    val agent: Agent
) : MessageHandler {
    override val messageType = RevocationNotificationMessageV1.type
    val supportedMessages = listOf(RevocationNotificationMessageV1::class)

    override suspend fun handle(messageContext: InboundMessageContext) : OutboundMessage? {
        agent.revocationNotificationService.v1ProcessRevocationNotification(messageContext)
        return null
    }
}

