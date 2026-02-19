package org.hyperledger.ariesframework.credentials.v2.handlers

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.MessageHandler
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.messages.CredentialAckMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.IssueCredentialMessageV2
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.util.LogUtil
import org.slf4j.LoggerFactory

class IssueCredentialHandlerV2(val agent: Agent) : MessageHandler {

    private val logger = LoggerFactory.getLogger(IssueCredentialHandlerV2::class.java)
    override val messageType = IssueCredentialMessageV2.type

    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
        LogUtil.info(this) { "issue credential - issue step" }

        // PrintLongLine.print("IssueCredentialHandlerV2 init: ${messageContext.plaintextMessage}") // aq ja tem o encode
        val credentialRecord = agent.credentialServiceV2.processCredential(messageContext)

        val shouldAutoRespond = agent.credentialServiceV2.shouldAutoRespondToCredential(
            credentialRecord = credentialRecord,
            messageContext = messageContext,
        )

        if (shouldAutoRespond) {
            val message = acceptCredential(credentialRecord)
            // logger.info("message accepted: $message")
            return OutboundMessage(message, messageContext.connection!!)
        }

        return null
    }

    private suspend fun acceptCredential(credentialRecord: CredentialExchangeRecord): CredentialAckMessageV2 {
        // logger.info("Automatically sending acknowledgement with autoAccept")

        val (_, ackMessage) = agent.credentialServiceV2.acceptCredential(credentialRecord)
            ?: throw CredoError("Failed to accept credential for record ID ${credentialRecord.id}")

        if (ackMessage == null) {
            throw CredoError("No acknowledgement message found for credential record ID ${credentialRecord.id}")
        }

        val request = agent.credentialServiceV2.findRequestMessage(credentialRecord.id)
            ?: throw CredoError("No request message found for credential record ID ${credentialRecord.id}")

        return ackMessage
    }
}
