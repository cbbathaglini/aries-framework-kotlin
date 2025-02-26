package org.hyperledger.ariesframework.didcomm

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.oob.repository.OutOfBandRecord
import org.hyperledger.ariesframework.oob.repository.OutOfBandRepository
import org.slf4j.LoggerFactory

class OutboundMessageContextUtil (val agent : Agent){
    private val logger = LoggerFactory.getLogger(OutboundMessageContextUtil::class.java)


    suspend fun getOutboundMessageContext(
        message: AgentMessage,
        connectionRecord: ConnectionRecord? = null,
        associatedRecord: BaseRecordAny? = null,
        lastReceivedMessage: AgentMessage? = null,
        lastSentMessage: AgentMessage? = null
    ): OutboundMessageContext {

        logger.debug("Creating outbound message context for message ${message.id}")

        connectionRecord?.let {
            return OutboundMessageContext(
                message = message,
                agentContext = agent,
                associatedRecord = associatedRecord,
                connection = connectionRecord
            )
        }

        if (lastReceivedMessage == null) {
            throw CredoError("No connection record and no lastReceivedMessage was supplied. For connection-less exchanges, lastReceivedMessage is required.")
        }

        if (associatedRecord == null) {
            throw CredoError("No associated record was supplied. This is required for connection-less exchanges to store the associated ~service decorator on the message.")
        }

        return getConnectionlessOutboundMessageContext(message, associatedRecord, lastReceivedMessage, lastSentMessage)
    }

    suspend fun getConnectionlessOutboundMessageContext(
        message: AgentMessage,
        associatedRecord: BaseRecordAny,
        lastReceivedMessage: AgentMessage,
        lastSentMessage: AgentMessage?
    ): OutboundMessageContext {
        logger.debug("Creating outbound message context for message ${message.id} using connection-less exchange")

        val outOfBandRecord = getOutOfBandRecordForMessage(agentContext, message)
        var (recipientService, ourService) = getServicesForMessage(agentContext, lastReceivedMessage, lastSentMessage, message, outOfBandRecord)

        if (lastSentMessage == null) {
            ourService = createOurService(agentContext, outOfBandRecord, message)
        }

        if (ourService == null) {
            throw CredoError("Could not determine our service for connection-less exchange for message ${message.id}.")
        }
        if (recipientService == null) {
            throw CredoError("Could not determine recipient service for connection-less exchange for message ${message.id}.")
        }

        addExchangeDataToMessage(agentContext, message, ourService, outOfBandRecord, associatedRecord)

        return OutboundMessageContext(
            message = message,
            agentContext = agentContext,
            associatedRecord = associatedRecord,
            serviceParams = OutboundMessageContext.ServiceParams(
                service = recipientService,
                senderKey = ourService.recipientKeys.first(),
                returnRoute = true
            )
        )
    }

    suspend fun getOutOfBandRecordForMessage(message: AgentMessage): OutOfBandRecord? {
        logger.debug("Looking for out-of-band record for message ${message.id} with thread ID ${message.threadId}")
        val outOfBandRepository = agentContext.dependencyManager.resolve(OutOfBandRepository::class.java)

        return outOfBandRepository.findSingleByQuery(
            mapOf("invitationRequestsThreadIds" to listOf(message.threadId))
        )
    }

    suspend fun getServicesForMessage(
        agentContext: AgentContext,
        lastReceivedMessage: AgentMessage,
        lastSentMessage: AgentMessage?,
        message: AgentMessage,
        outOfBandRecord: OutOfBandRecord?
    ): Pair<ResolvedDidCommService?, ResolvedDidCommService?> {
        var ourService = lastSentMessage?.service?.resolvedDidCommService
        var recipientService = lastReceivedMessage.service?.resolvedDidCommService

        val outOfBandService = agentContext.dependencyManager.resolve(OutOfBandService::class.java)

        if (outOfBandRecord?.role == OutOfBandRole.SENDER) {
            if (ourService == null) {
                ourService = outOfBandService.getResolvedServiceForOutOfBandServices(
                    agentContext, outOfBandRecord.outOfBandInvitation.getServices()
                )
            }

            if (recipientService == null) {
                throw CredoError("Could not find a service to send the message to. Please make sure the connection has a service.")
            }

            if (lastSentMessage == null) {
                throw CredoError("Must have lastSentMessage when out of band record has role Sender")
            }
        } else if (outOfBandRecord?.role == OutOfBandRole.RECEIVER) {
            if (recipientService == null) {
                recipientService = outOfBandService.getResolvedServiceForOutOfBandServices(
                    agentContext, outOfBandRecord.outOfBandInvitation.getServices()
                )
            }

            if (lastSentMessage != null && ourService == null) {
                throw CredoError("Could not find a service to send the message to. Please make sure the connection has a service.")
            }
        } else {
            if (lastSentMessage != null && ourService == null) {
                agentContext.config.logger.error("Missing our service for connection-less exchange for message ${message.id}")
                throw CredoError("Missing our service for connection-less exchange for message ${message.id}")
            }

            if (recipientService == null) {
                agentContext.config.logger.error("Missing recipient service for connection-less exchange for message ${message.id}")
                throw CredoError("Missing recipient service for connection-less exchange for message ${message.id}")
            }
        }

        return recipientService to ourService
    }

    suspend fun createOurService(
        agentContext: AgentContext,
        outOfBandRecord: OutOfBandRecord?,
        message: AgentMessage
    ): ResolvedDidCommService {
        agentContext.config.logger.debug("No previous sent message in thread for outbound message ${message.id}, setting up routing")

        val routingService = agentContext.dependencyManager.resolve(RoutingService::class.java)
        val routing = routingService.getRouting(agentContext, outOfBandRecord?.mediatorId)

        return ResolvedDidCommService(
            id = UUIDUtils.generateUuid(),
            serviceEndpoint = routing.endpoints.first(),
            recipientKeys = listOf(routing.recipientKey),
            routingKeys = routing.routingKeys
        )
    }

    suspend fun addExchangeDataToMessage(
        agentContext: AgentContext,
        message: AgentMessage,
        ourService: ResolvedDidCommService,
        outOfBandRecord: OutOfBandRecord?,
        associatedRecord: BaseRecordAny
    ) {
        outOfBandRecord?.let {
            message.thread?.parentThreadId = it.outOfBandInvitation.id
        }

        message.service = ourService.toServiceDecorator()

        val didCommMessageRepository = agentContext.dependencyManager.resolve(DidCommMessageRepository::class.java)
        didCommMessageRepository.saveOrUpdateAgentMessage(
            agentContext,
            agentMessage = message,
            role = DidCommMessageRole.SENDER,
            associatedRecordId = associatedRecord.id
        )
    }
}