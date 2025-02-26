package org.hyperledger.ariesframework.credentialsv2.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.credentialsv2.messages.CredentialAckMessageV2

class CredentialAckHandlerV2(val agent: Agent) : MessageHandler {

    override val messageType = CredentialAckMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        agent.credentialService.processAck(messageContext)
        return null
    }
}