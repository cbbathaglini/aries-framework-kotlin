package org.hyperledger.ariesframework.problemreports.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.credentials.v2.CredentialServiceV2
import org.hyperledger.ariesframework.problemreports.messages.BaseProblemReportMessage
import org.slf4j.LoggerFactory

//class ProblemReportNotificationHandler(val agent: Agent, override val messageType: String) : MessageHandler {
//    private val logger = LoggerFactory.getLogger(ProblemReportNotificationHandler::class.java)
//
//    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
//        val message = messageContext.message as BaseProblemReportMessage
//        logger.error("[IDD] Problem report: ${message.toJsonString()}")
//        agent.eventBus.publish(AgentEvents.ProblemReportNotificationEvent(message))
//        return null
//    }
//}
