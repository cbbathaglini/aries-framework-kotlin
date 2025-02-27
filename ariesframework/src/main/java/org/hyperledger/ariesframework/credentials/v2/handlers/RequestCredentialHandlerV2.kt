package org.hyperledger.ariesframework.credentials.v2.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.credentials.v1.AcceptRequestOptions
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.v2.messages.RequestCredentialMessageV2
import org.slf4j.LoggerFactory
import kotlin.math.log


class RequestCredentialHandlerV2(val agent: Agent) : MessageHandler {

    private val logger = LoggerFactory.getLogger(RequestCredentialHandlerV2::class.java)
    override val messageType =  RequestCredentialMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        logger.info("[IDD] Initializing handle of RequestCredentialHandlerV2")
        val credentialRecord = agent.credentialServiceV2.processRequestCredentialMessage(messageContext)

        if (credentialRecord.autoAcceptCredential == AutoAcceptCredential.Always ||
            agent.agentConfig.autoAcceptCredential == AutoAcceptCredential.Always
        ) {
            val message = agent.credentialServiceV2.createIssueCredentialMessageV2(AcceptRequestOptions(credentialRecord.id))
            return null //OutboundMessage(message, messageContext.connection!!)
        }

        return null
    }
}