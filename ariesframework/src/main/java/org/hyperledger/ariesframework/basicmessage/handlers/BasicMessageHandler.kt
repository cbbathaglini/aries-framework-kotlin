package org.hyperledger.ariesframework.basicmessage.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.basicmessage.messages.BasicMessage
import org.hyperledger.ariesframework.basicmessage.messages.BasicMessageInfos

class BasicMessageHandler(val agent: Agent) : MessageHandler {
    override val messageType = BasicMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val message = messageContext.message as BasicMessage
        val connectionRecordId = messageContext.connection?.id
        val theirLabel = messageContext.connection?.theirLabel
        val createdAt = messageContext.connection?.createdAt
        val basicMessageInfos = BasicMessageInfos(
            content = message.content,
            connectionRecordId = connectionRecordId,
            theirLabel = theirLabel,
            createdAt = createdAt,
        )
        agent.eventBus.publish(AgentEvents.BasicMessageEvent(basicMessageInfos))
        return null
    }
}
