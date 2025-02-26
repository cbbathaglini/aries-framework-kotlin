package org.hyperledger.ariesframework.protocols.credentials

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRepository
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentialsv2.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentialsv2.models.CreateProposalOptionsV2
import org.hyperledger.ariesframework.credentialsv2.models.CredentialProtocolMsgReturnType
import org.hyperledger.ariesframework.credentialsv2.models.CredentialRole
import org.hyperledger.ariesframework.credentialsv2.models.Format
import org.hyperledger.ariesframework.didcomm.FeatureRegistry
import org.hyperledger.ariesframework.didcomm.models.Protocol
import org.hyperledger.ariesframework.didcomm.models.ProtocolOptions
import org.hyperledger.ariesframework.error.CredoError
import org.slf4j.LoggerFactory
import java.util.UUID


class CredentialServiceV2(val agent: Agent) {
    private val logger = LoggerFactory.getLogger(CredentialServiceV2::class.java)
    private val credentialFormatCoordinator = CredentialFormatCoordinator<CFs>()

    private val credentialRepository = agent.credentialRepository
    private val didCommMessageRepository = agent.didCommMessageRepository
    private val connectionService = agent.connectionService

    suspend fun createProposal(options: CreateProposalOptionsV2): CredentialProtocolMsgReturnType<AgentMessage> {
        logger.debug("Get the Format Service and Create Proposal Message")

        val formatServices = getFormatServices(options.credentialFormats)

        if (formatServices.isEmpty()) {
            throw CredoError("Unable to create proposal. No supported formats")
        }

        val credentialRecord = CredentialExchangeRecord(
            connectionId = options.connection.id,
            threadId = UUID.randomUUID().toString(),
            state = CredentialState.ProposalSent,
            role = CredentialRole.Holder,
            autoAcceptCredential = options.autoAcceptCredential,
            protocolVersion = "v2"
        )

        val proposalMessage = credentialFormatCoordinator.createProposal(
            options.copy(
                credentialRecord = credentialRecord,
                formatServices = formatServices)
        )

        logger.debug("Save record and emit state change event")
        credentialRepository.save(credentialRecord)
        emitStateChangedEvent(agentContext, credentialRecord, null)

        return  CredentialProtocolMsgReturnType(
                    credentialRecord = credentialRecord,
                    message = proposalMessage
        )
    }

    suspend fun processProposal(
        messageContext: InboundMessageContext<V2ProposeCredentialMessage>
    ): CredentialExchangeRecord {
        val (proposalMessage, connection, agentContext) = messageContext
        agentContext.config.logger.debug("Processing credential proposal with id ${proposalMessage.id}")

        val credentialRepository = agentContext.dependencyManager.resolve(CredentialRepository::class.java)
        val didCommMessageRepository = agentContext.dependencyManager.resolve(DidCommMessageRepository::class.java)
        val connectionService = agentContext.dependencyManager.resolve(ConnectionService::class.java)

        var credentialRecord = findByProperties(agentContext, CredentialRecordProperties(
            threadId = proposalMessage.threadId,
            role = CredentialRole.Issuer
        ))

        val formatServices = getFormatServicesFromMessage(proposalMessage.formats)
        if (formatServices.isEmpty()) {
            throw CredoError("Unable to process proposal. No supported formats")
        }

        if (credentialRecord != null) {
            val proposalCredentialMessage = didCommMessageRepository.findAgentMessage(agentContext, credentialRecord.id, V2ProposeCredentialMessage::class, DidCommMessageRole.Receiver)
            val offerCredentialMessage = didCommMessageRepository.findAgentMessage(agentContext, credentialRecord.id, V2OfferCredentialMessage::class, DidCommMessageRole.Sender)

            credentialRecord.assertProtocolVersion("v2")
            credentialRecord.assertState(CredentialState.OfferSent)
            connectionService.assertConnectionOrOutOfBandExchange(messageContext, proposalCredentialMessage, offerCredentialMessage, credentialRecord.connectionId)

            credentialRecord.connectionId = credentialRecord.connectionId ?: connection?.id

            credentialFormatCoordinator.processProposal(agentContext, credentialRecord, formatServices, proposalMessage)

            updateState(agentContext, credentialRecord, CredentialState.ProposalReceived)
            return credentialRecord
        } else {
            connectionService.assertConnectionOrOutOfBandExchange(messageContext)

            credentialRecord = CredentialExchangeRecord(
                connectionId = connection?.id,
                threadId = proposalMessage.threadId,
                parentThreadId = proposalMessage.thread?.parentThreadId,
                state = CredentialState.ProposalReceived,
                role = CredentialRole.Issuer,
                protocolVersion = "v2"
            )

            credentialFormatCoordinator.processProposal(agentContext, credentialRecord, formatServices, proposalMessage)

            credentialRepository.save(agentContext, credentialRecord)
            emitStateChangedEvent(agentContext, credentialRecord, null)

            return credentialRecord
        }
    }

    private fun getFormatServicesFromMessage(messageFormats: List<Format>): List<CredentialFormatService> {
        return messageFormats.mapNotNull { getFormatServiceForFormat(it.format) }
    }

    private fun getFormatServices(credentialFormats: Map<String, Any>): List<CredentialFormatService> {
        return credentialFormats.keys.mapNotNull { getFormatServiceForFormatKey(it) }
    }

    fun getFormatServices(
        credentialFormats: Map<String, Any?>,
        getFormatServiceForFormatKey: (String) -> CredentialFormatService?
    ): List<CredentialFormatService> {
        val formats = mutableSetOf<CredentialFormatService>()

        for (formatKey in credentialFormats.keys) {
            getFormatServiceForFormatKey(formatKey)?.let { formats.add(it) }
        }

        return formats.toList()
    }

    private fun getFormatServiceForFormatKey(formatKey: String): CredentialFormatService? {
        return credentialFormats.find { it.formatKey == formatKey }
    }

    private fun getFormatServiceForFormat(format: String): CredentialFormatService? {
        return credentialFormats.find { it.supportsFormat(format) }
    }

    private suspend fun updateState(agentContext: AgentContext, credentialRecord: CredentialExchangeRecord, state: CredentialState) {
        credentialRecord.state = state
        agentContext.dependencyManager.resolve(CredentialRepository::class.java).update(agentContext, credentialRecord)
    }
}