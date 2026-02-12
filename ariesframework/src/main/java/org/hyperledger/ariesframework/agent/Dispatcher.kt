package org.hyperledger.ariesframework.agent

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.util.LogUtil

class Dispatcher(val agent: Agent) {
    var handlers = mutableMapOf<String, MessageHandler>()

    fun registerHandler(handler: MessageHandler) {
        handlers[handler.messageType] = handler
    }

    suspend fun dispatch(messageContext: InboundMessageContext) {
        LogUtil.info(this) { "Dispatching message of type: ${messageContext.message.type}" }

        val handler = handlers[messageContext.message.type]
            ?: throw Exception("No handler for message type: ${messageContext.message.type} - ${messageContext.plaintextMessage}")

        try {
            val outboundMessage = handler.handle(messageContext)

            if (outboundMessage != null) {
                LogUtil.info(this) { "Finishing dispatch with message of type: ${outboundMessage.payload.type}" }
                agent.messageSender.send(outboundMessage)
            } else {
                LogUtil.info(this) { "Finishing dispatch without response" }
            }
        } catch (e: Exception) {
            LogUtil.error(this, e) { "Failed to dispatch message of type: ${messageContext.message.type}" }
            throw e
        }
    }

//    private fun printDispatcherMessages(messageContext: InboundMessageContext) {
//        logger.info("message: ${messageContext.message}")
//        logger.info("plaintextMessage: ${messageContext.plaintextMessage}")
//        logger.info("type of message: ${messageContext.message.type}")
//
//        logger.info("all handlers available -->")
//        handlers.forEach { (key, value) ->
//            logger.info("Type = $key, Handler = $value")
//        }
//    }

    fun getHandlerForType(messageType: String): MessageHandler? {
        return handlers[messageType]
    }

    fun canHandleMessage(message: AgentMessage): Boolean {
        return handlers[message.type] != null
    }
}
