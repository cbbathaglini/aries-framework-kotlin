package org.hyperledger.ariesframework.agent

import org.hyperledger.ariesframework.DecryptedMessageContext
import org.hyperledger.ariesframework.EncryptedMessage
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.connection.models.didauth.DidCommService
import org.hyperledger.ariesframework.connection.models.didauth.DidDoc
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.routing.Routing
import org.hyperledger.ariesframework.util.LogUtil

class MessageReceiver(val agent: Agent) {

    suspend fun receiveMessage(encryptedMessage: EncryptedMessage) {
        try {
            val decryptedMessage = agent.wallet.unpack(encryptedMessage)
            LogUtil.info(this) { "decrypted message: $decryptedMessage" }

            val message = MessageSerializer.decodeFromString(decryptedMessage.plaintextMessage)
            val connection = findConnection(decryptedMessage, message)
            val messageContext = InboundMessageContext(
                message,
                decryptedMessage.plaintextMessage,
                connection,
                decryptedMessage.senderKey,
                decryptedMessage.recipientKey,
            )

            agent.dispatcher.dispatch(messageContext)
        } catch (e: Exception) {
            LogUtil.error(this, e) { "failed to receive message: ${e.message}" }
        }
    }

    suspend fun receivePlaintextMessage(plaintextMessage: String, connection: ConnectionRecord) {
        try {
            val message = MessageSerializer.decodeFromString(plaintextMessage)
            val messageContext = InboundMessageContext(
                message,
                plaintextMessage,
                connection,
                null,
                null,
            )
            agent.dispatcher.dispatch(messageContext)
        } catch (e: Exception) {
            LogUtil.error(this, e) { "failed to receive message: ${e.message}" }
        }
    }

    private suspend fun findConnection(decryptedMessage: DecryptedMessageContext, message: AgentMessage): ConnectionRecord? {
        var connection = findConnectionByMessageKeys(decryptedMessage)
        if (connection == null) {
            connection = findConnectionByMessageThreadId(message)
            if (connection != null) {
                updateConnectionTheirDidDoc(connection, decryptedMessage.senderKey)
            }
        }
        return connection
    }

    private suspend fun findConnectionByMessageThreadId(message: AgentMessage): ConnectionRecord? {
        val pthId = message.thread?.parentThreadId ?: ""
        val oobRecord = agent.outOfBandService.findByInvitationId(pthId)
        val invitationKey = oobRecord?.outOfBandInvitation?.invitationKey() ?: ""
        return agent.connectionService.findByInvitationKey(invitationKey)
    }

    private suspend fun updateConnectionTheirDidDoc(connection: ConnectionRecord, senderKey: String?) {
        if (senderKey == null) {
            return
        }
        val service = DidCommService(
            id = "${connection.id}#1",
            serviceEndpoint = Routing.DID_COMM_TRANSPORT_QUEUE,
            recipientKeys = listOf(senderKey),
        )

        val theirDidDoc = DidDoc(
            id = senderKey,
            publicKey = emptyList(),
            service = listOf(service),
            authentication = emptyList(),
        )
        connection.theirDidDoc = theirDidDoc
        agent.connectionRepository.update(connection)
    }

    private suspend fun findConnectionByMessageKeys(decryptedMessage: DecryptedMessageContext): ConnectionRecord? {
//        logger.info("findConnectionByMessageKeys method")
//        logger.info("decryptedMessage.senderKey: ${decryptedMessage.senderKey}")
//        logger.info("decryptedMessage.recipientKey: ${decryptedMessage.recipientKey}")

        val conn = agent.connectionService.findByKeys(
            decryptedMessage.senderKey ?: "",
            decryptedMessage.recipientKey ?: "",
        )

        return conn
    }
}
