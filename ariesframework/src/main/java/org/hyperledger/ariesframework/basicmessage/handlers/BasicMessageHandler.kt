package org.hyperledger.ariesframework.basicmessage.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.basicmessage.messages.BasicMessage
import org.hyperledger.ariesframework.basicmessage.repository.BasicMessageRecord
import org.hyperledger.ariesframework.history.models.HistoryType
import org.hyperledger.ariesframework.history.repository.HistoryRecord
import org.hyperledger.ariesframework.util.LogUtil

class BasicMessageHandler(val agent: Agent) : MessageHandler {
    override val messageType = BasicMessage.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        LogUtil.info(this) { "basic message handling" }
        val message = messageContext.message as BasicMessage

        val basicMessageRecord = BasicMessageRecord(
            content = message.content,
            connectionRecord = messageContext.connection,
        )

        if (messageContext.connection != null) {
            agent.historyRepository.save(
                HistoryRecord(
                    historyType = HistoryType.BasicMessageReceived.name,
                    connectionId = messageContext.connection.id,
                    theirLabel = messageContext.connection.theirLabel,
                    associatedRecordId = basicMessageRecord.id,
                    content = basicMessageRecord.content,
                ),
            )
        }

        agent.eventBus.publish(AgentEvents.BasicMessageEvent(basicMessageRecord))

        return null
    }
}
