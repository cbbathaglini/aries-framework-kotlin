package org.hyperledger.ariesframework.proofs.v2

import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.history.models.HistoryType
import org.hyperledger.ariesframework.history.repository.HistoryRecord
import org.hyperledger.ariesframework.proofs.models.AcceptProofRequestOptions
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.proofs.models.CreateProofRequestOptions
import org.hyperledger.ariesframework.proofs.models.ProofFormatSpec
import org.hyperledger.ariesframework.proofs.models.RequestedCredentialsAnoncreds
import org.hyperledger.ariesframework.proofs.models.RetrievedCredentialsAnonCreds
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.proofs.repository.verifier.VerifierRecord
import org.hyperledger.ariesframework.proofs.v2.handlers.PresentationAckHandlerV2
import org.hyperledger.ariesframework.proofs.v2.handlers.PresentationHandlerV2
import org.hyperledger.ariesframework.proofs.v2.handlers.RequestPresentationHandlerV2
import org.hyperledger.ariesframework.proofs.v2.messages.PresentationAckMessageV2
import org.hyperledger.ariesframework.proofs.v2.messages.PresentationMessageV2
import org.hyperledger.ariesframework.proofs.v2.messages.RequestPresentationMessageV2
import org.slf4j.LoggerFactory

class ProofCommandV2(val agent: Agent, private val dispatcher: Dispatcher) {
    private val logger = LoggerFactory.getLogger(ProofCommandV2::class.java)

    init {
        registerHandlers(dispatcher)
        registerMessages()
    }

    private fun registerHandlers(dispatcher: Dispatcher) {
        dispatcher.registerHandler(RequestPresentationHandlerV2(agent))
        dispatcher.registerHandler(PresentationHandlerV2(agent))
        dispatcher.registerHandler(PresentationAckHandlerV2(agent))
    }

    private fun registerMessages() {
        MessageSerializer.registerMessage(PresentationMessageV2.type, PresentationMessageV2::class)
        MessageSerializer.registerMessage(
            RequestPresentationMessageV2.type,
            RequestPresentationMessageV2::class,
        )
        MessageSerializer.registerMessage(
            PresentationAckMessageV2.type,
            PresentationAckMessageV2::class,
        )
    }

    suspend fun requestProof(
        connectionId: String,
        proofRequest: AnonCredsProofRequest,
        formats: List<ProofFormatSpec> = emptyList(),
        autoAcceptProof: AutoAcceptProof? = null,
        willConfirm: Boolean? = null,
        comment: String? = null,
    ): Pair<ProofExchangeRecord, VerifierRecord> {
        val connection = agent.connectionRepository.getById(connectionId)

        val format: String = formats.first().attachmentId ?: throw CredoError("Proof format not informed")

        val proofFormats: Map<String, JsonElement> = ProofUtils.getProofFormats(proofRequest, format)
        // logger.info("proof formats: $proofFormats")
        // logger.info("proof request >>> ${proofRequest.toJson()}")

        val (message, record) = agent.proofServiceV2.createRequest(
            CreateProofRequestOptions(
                connectionRecord = connection,
                proofRequest = proofRequest,
                comment = comment,
                autoAcceptProof = autoAcceptProof ?: AutoAcceptProof.Never,
                willConfirm = willConfirm,
                formats = formats,
                proofFormats = proofFormats,
            ),
        )

        // Log.d("MAIN_MESSAGE", "requestProof")
        agent.messageSender.send(OutboundMessage(message, connection))

        val verifierRecord = VerifierRecord(
            proofRequest = proofRequest,
            requestMessage = message,
            globalThreadId = record.threadId,
            offline = false,
        )

        agent.verifierRepository.save(verifierRecord)

        return Pair(record, verifierRecord)
    }

    suspend fun requestProofOffline(
        proofRequest: AnonCredsProofRequest,
        formats: List<ProofFormatSpec> = emptyList(),
        autoAcceptProof: AutoAcceptProof? = null,
        willConfirm: Boolean? = null,
        comment: String? = null,
    ): Pair<ProofExchangeRecord, VerifierRecord> {
        try {
            val format = formats.firstOrNull()?.attachmentId
                ?: throw Exception("Proof format not informed")

            val proofFormats = ProofUtils.getProofFormats(
                proofRequest = proofRequest,
                format = format,
            )

            val (message, record) = agent.proofServiceV2.createRequest(
                CreateProofRequestOptions(
                    proofRequest = proofRequest,
                    formats = formats,
                    proofFormats = proofFormats,
                    connectionRecord = null,
                    comment = comment,
                    autoAcceptProof = autoAcceptProof ?: AutoAcceptProof.Never,
                    willConfirm = willConfirm,
                ),
            )

            val verifierRecord = VerifierRecord(
                proofRequest = proofRequest,
                requestMessage = message,
                globalThreadId = record.threadId,
                offline = true,
            )

            agent.verifierRepository.save(verifierRecord)

            return Pair(record, verifierRecord)
        } catch (e: Exception) {
            // logger.error("Error creating offline proof: ${e.message}", e)
            throw e
        }
    }

    suspend fun processRequest(
        requestMessage: RequestPresentationMessageV2,
    ): ProofExchangeRecord {
        return agent.proofServiceV2.processRequest(requestMessage = requestMessage)
    }

    suspend fun processPresentationOffline(presentationMessage: String): Pair<ProofExchangeRecord, Boolean> {
        val message = MessageSerializer.decodeFromString(presentationMessage) as? PresentationMessageV2
            ?: throw Exception("Failed to decode PresentationMessageV2")

        val proofRecord = agent.proofServiceV2.processPresentationOffline(message)
            ?: throw Exception("Failed to process presentation")

        val result = proofRecord.isVerified ?: false
        return Pair(proofRecord, result)
    }

    suspend fun processOfflineAck(proofRecord: ProofExchangeRecord) {
        agent.proofServiceV2.processOfflineAck(proofRecord)
    }

    suspend fun createPresentation(
        record: ProofExchangeRecord,
        chosenCredentialId: String? = null,
    ): Pair<ProofExchangeRecord, PresentationMessageV2> {
        var chosenCredential: CredentialExchangeRecord? = null
        if (chosenCredentialId != null) {
            chosenCredential =
                agent.credentialExchangeRepository.getById(chosenCredentialId)
        }

        logger.info("Chosen credential: ${chosenCredential?.w3cCredentialId ?: "none credential"}")

        val retrievedCredentials = ProofUtils.getRequestedCredentialsForProofRequest(
            proofRecordId = record.id,
            agent = agent,
            credentialW3cId = chosenCredential?.w3cCredentialId,
        )

        val requestedCredentials: RequestedCredentialsAnoncreds =
            agent.proofServiceV2.autoSelectCredentialsForProofRequest(retrievedCredentials)

        val requestedCredentialsMap = requestedCredentials.toMap()
        logger.info("map requested: $requestedCredentialsMap")
        val params = AcceptProofRequestOptions(
            proofRecord = record,
            proofFormats = record.formats ?: emptyList(),
            requestedCredentials = requestedCredentialsMap,
            chosenCredentialId = chosenCredential?.w3cCredentialId,
        )

        val (message, _) = agent.proofServiceV2.acceptRequest(params)
        return Pair(record, message)
    }

    suspend fun acceptRequest(
        proofRecordId: String,
        chosenCredentialId: String? = null,
        comment: String? = null,
    ): ProofExchangeRecord {
        var chosenCredential: CredentialExchangeRecord? = null
        if (chosenCredentialId != null) {
            chosenCredential =
                agent.credentialExchangeRepository.getById(chosenCredentialId)
        }

        val retrievedCredentials: RetrievedCredentialsAnonCreds =
            ProofUtils.getRequestedCredentialsForProofRequest(
                proofRecordId = proofRecordId,
                agent = agent,
                credentialW3cId = chosenCredential?.w3cCredentialId,
            )

        val requestedCredentials: RequestedCredentialsAnoncreds =
            agent.proofServiceV2.autoSelectCredentialsForProofRequest(retrievedCredentials)

        val msg = agent.didCommMessageRepository.getAgentMessage(
            proofRecordId,
            RequestPresentationMessageV2.type,
        )

        val record = agent.proofRepository.getById(proofRecordId)

        val requestedCredentialsMap = requestedCredentials.toMap()
        logger.info("map requested: $requestedCredentialsMap")
        val params = AcceptProofRequestOptions(
            proofRecord = record,
            proofFormats = record.formats!!,
            comment = comment,
            requestedCredentials = requestedCredentialsMap,
            chosenCredentialId = chosenCredential?.w3cCredentialId,
        )

        val (message, proofRecord) = agent.proofServiceV2.acceptRequest(params)

        val connection = agent.connectionRepository.getById(record.connectionId)

        requestedCredentials.normalizeAllAttributes()
        logger.info("requestedCredentials: $requestedCredentials")

        agent.historyRepository.save(
            HistoryRecord(
                historyType = HistoryType.ProofRequestAccepted.name,
                connectionId = connection.id,
                theirLabel = connection.theirLabel,
                associatedRecordId = proofRecordId,
                proofRequestedCredentialsAnoncreds = requestedCredentials,
            ),
        )

        agent.messageSender.send(OutboundMessage(message, connection))
        return proofRecord
    }

    suspend fun declineRequest(
        proofRecordId: String,
    ): ProofExchangeRecord {
        val record = agent.proofRepository.getById(proofRecordId)
        val (message, proofRecord) = agent.proofServiceV2.createPresentationDeclinedProblemReport(
            record,
        )

        val connection = agent.connectionRepository.getById(record.connectionId)
        agent.messageSender.send(OutboundMessage(message, connection))

        agent.historyRepository.save(
            HistoryRecord(
                historyType = HistoryType.ProofRequestDeclined.name,
                connectionId = connection.id,
                theirLabel = connection.theirLabel,
                associatedRecordId = proofRecordId,
            ),
        )

        return proofRecord
    }
}
