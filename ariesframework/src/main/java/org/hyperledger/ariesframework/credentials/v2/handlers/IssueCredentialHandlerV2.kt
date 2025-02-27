package org.hyperledger.ariesframework.credentials.v2.handlers

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.credentials.v2.messages.IssueCredentialMessageV2
import org.slf4j.LoggerFactory

class IssueCredentialHandlerV2(val agent: Agent)  {//: MessageHandler {

    //override val messageType = IssueCredentialMessageV2.type

    private val logger = LoggerFactory.getLogger(IssueCredentialHandlerV2::class.java)
    val supportedMessages = listOf(IssueCredentialMessageV2::class)

//    override suspend fun handle(messageContext: InboundMessageContext): OutboundMessage? {
//        val credentialRecord = agent.credentialServiceV2.processCredential(messageContext)
//
//        val shouldAutoRespond = agent.credentialServiceV2.shouldAutoRespondToCredential(
//            credentialRecord,
//            messageContext.message
//        )
//
//        if (shouldAutoRespond) {
//            acceptCredential(credentialRecord, messageContext)
//        }
//    }
//
//    private suspend fun acceptCredential(
//        credentialRecord: CredentialExchangeRecord,
//        messageContext: InboundMessageContext
//    ) {
//        logger.info("Automatically sending acknowledgement with autoAccept")
//
//        val message = agent.credentialServiceV2.acceptCredential(
//            credentialRecord
//        ).message
//
//        val requestMessage = agent.credentialServiceV2.findRequestMessage(
//            credentialRecord.id
//        )
//
//        if (requestMessage == null) {
//            throw CredoError("No request message found for credential record with id '${credentialRecord.id}'")
//        }
//
//        getOutboundMessageContext(
//            connectionRecord = messageContext.connection,
//            message = message,
//            associatedRecord = credentialRecord,
//            lastReceivedMessage = messageContext.message,
//            lastSentMessage = requestMessage
//        )
//    }
}