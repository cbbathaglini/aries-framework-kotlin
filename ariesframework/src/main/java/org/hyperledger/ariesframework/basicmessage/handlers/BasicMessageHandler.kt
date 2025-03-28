package org.hyperledger.ariesframework.basicmessage.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.basicmessage.messages.BasicMessage
import org.hyperledger.ariesframework.basicmessage.repository.BasicMessageRecord

class BasicMessageHandler(val agent: Agent) : MessageHandler {
    override val messageType = BasicMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        val message = messageContext.message as BasicMessage

        val basicMessageRecord = BasicMessageRecord(
            content = message.content,
            connectionRecord = messageContext.connection,
        )

        agent.basicMessageRepository.save(basicMessageRecord)

        agent.eventBus.publish(AgentEvents.BasicMessageEvent(basicMessageRecord))

        return null
    }
}
