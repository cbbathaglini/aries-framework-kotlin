package org.hyperledger.ariesframework.agent

import org.hyperledger.ariesframework.InboundMessageContext
import org.slf4j.LoggerFactory

class Dispatcher(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(Dispatcher::class.java)
    var handlers = mutableMapOf<String, MessageHandler>()

    fun registerHandler(handler: MessageHandler) {
        logger.info("Listing all my handlers: ${handlers.keys}")
        handlers[handler.messageType] = handler
        handlers[replaceNewDidCommPrefixWithLegacyDidSov(handler.messageType)] = handler
    }

    suspend fun dispatch(messageContext: InboundMessageContext) {
        logger.debug("Dispatching message of type: ${messageContext.message.type}")

        logger.info("[IDD][DISPATCHER] my message: ${messageContext.message}")
        logger.info("[IDD][DISPATCHER] my plaintextMessage: ${messageContext.plaintextMessage}")
        logger.info("[IDD][DISPATCHER] my type of message: ${messageContext.message.type}")
//        logger.info("[IDD] all handlers available --------------------------------------------------------")
//        handlers.forEach { (key, value) ->
//            logger.info("[IDD] Available handler: Type = $key, Handler = $value")
//        }

        val handler = handlers[messageContext.message.type]
            ?: throw Exception("No handler for message type: ${messageContext.message.type}")

        try {
            logger.info("[IDD][DISPATCHER] try catch ")
            val outboundMessage = handler.handle(messageContext)
            if (outboundMessage != null) {
                logger.info("[IDD][DISPATCHER] outb != null ")
                logger.debug("Finishing dispatch with message of type: ${outboundMessage.payload.type}")
                agent.messageSender.send(outboundMessage)
            } else {
                logger.info("[IDD][DISPATCHER]  outb == null")
                logger.debug("Finishing dispatch without response")
            }
        } catch (e: Exception) {
            logger.error("Failed to dispatch message of type: ${messageContext.message.type}")
            throw e
        }
    }

    fun getHandlerForType(messageType: String): MessageHandler? {
        return handlers[messageType]
    }

    fun canHandleMessage(message: AgentMessage): Boolean {
        return handlers[message.type] != null
    }

    companion object {
        fun replaceNewDidCommPrefixWithLegacyDidSov(messageType: String): String {
            val didSovPrefix = "did:sov:BzCbsNYhMrjHiqZDTUASHg;spec"
            val didCommPrefix = "https://didcomm.org"

            if (messageType.startsWith(didCommPrefix)) {
                return messageType.replace(didCommPrefix, didSovPrefix)
            }

            return messageType
        }
    }
}
