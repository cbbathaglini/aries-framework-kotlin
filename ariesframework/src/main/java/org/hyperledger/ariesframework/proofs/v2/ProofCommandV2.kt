package org.hyperledger.ariesframework.proofs.v2

import android.util.Log
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.Dispatcher
import org.hyperledger.ariesframework.agent.MessageSerializer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.history.models.HistoryType
import org.hyperledger.ariesframework.history.repository.HistoryRecord
import org.hyperledger.ariesframework.proofs.handlers.v2.PresentationAckHandlerV2
import org.hyperledger.ariesframework.proofs.handlers.v2.PresentationHandlerV2
import org.hyperledger.ariesframework.proofs.handlers.v2.RequestPresentationHandlerV2
import org.hyperledger.ariesframework.proofs.messages.v2.PresentationAckMessageV2
import org.hyperledger.ariesframework.proofs.messages.v2.PresentationMessageV2
import org.hyperledger.ariesframework.proofs.messages.v2.RequestPresentationMessageV2
import org.hyperledger.ariesframework.proofs.models.AcceptProofRequestOptions
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.proofs.models.CreateProofRequestOptions
import org.hyperledger.ariesframework.proofs.models.ProofFormatSpec
import org.hyperledger.ariesframework.proofs.models.RequestedCredentialsAnoncreds
import org.hyperledger.ariesframework.proofs.models.RetrievedCredentials
import org.hyperledger.ariesframework.proofs.models.RetrievedCredentialsAnonCreds
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
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

    /**
     * Initiate a new presentation exchange as verifier by sending a presentation request message
     * to the connection with the specified connection id.
     *
     * @param connectionId the connection to send the proof request to.
     * @param proofRequest the proof request to send.
     * @param comment a comment to include in the proof request message.
     * @param autoAcceptProof whether to automatically accept the proof message.
     * @return a new proof record for the proof exchange.
     */
    suspend fun requestProof(
        connectionId: String,
        proofRequest: AnonCredsProofRequest,
        comment: String? = null,
        autoAcceptProof: AutoAcceptProof?,
        willConfirm: Boolean?,
    ): ProofExchangeRecord {
        val connection = agent.connectionRepository.getById(connectionId)

        val (message, record) = agent.proofServiceV2.createRequest(
            CreateProofRequestOptions(
                connectionRecord = connection,
                proofRequest = proofRequest,
                comment = comment,
                autoAcceptProof = autoAcceptProof ?: AutoAcceptProof.Never,
                willConfirm = willConfirm,
            ),
        )

        Log.d("MAIN_MESSAGE", "requestProof")
        agent.messageSender.send(OutboundMessage(message, connection))

        return record
    }

    /**
     * Accept a presentation request as prover (by sending a presentation message) to the connection
     * associated with the proof record.
     *
     * @param proofRecordId the id of the proof record for which to accept the request.
     * @param requestedCredentials the requested credentials object specifying which credentials to use for the proof.
     * @param comment a comment to include in the presentation message.
     * @return proof record associated with the sent presentation message.
     */
    suspend fun acceptRequest(
        proofRecordId: String,
        comment: String? = null,
    ): ProofExchangeRecord {
        val retrievedCredentials: RetrievedCredentialsAnonCreds =
            getRequestedCredentialsForProofRequest(proofRecordId)
        val requestedCredentials: RequestedCredentialsAnoncreds =
            agent.proofServiceV2.autoSelectCredentialsForProofRequest(retrievedCredentials)

        val msg = agent.didCommMessageRepository.getAgentMessage(
            proofRecordId,
            RequestPresentationMessageV2.type,
        )

        val record = agent.proofRepository.getById(proofRecordId)

        val requestedCredentialsMap = requestedCredentials.toMap()
        val params = AcceptProofRequestOptions(
            proofRecord = record,
            proofFormats = record.formats!!,
            comment = comment,
            requestedCredentials = requestedCredentialsMap,
        )

        val (message, proofRecord) = agent.proofServiceV2.acceptRequest(params)

        val connection = agent.connectionRepository.getById(record.connectionId)

        requestedCredentials.normalizeAllAttributes()

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

    /**
     * Decline a presentation request as prover (by sending a problem report message) to the connection
     * associated with the proof record.
     *
     * @param proofRecordId the id of the proof record for which to decline the request.
     * @return proof record associated with the sent presentation request message.
     */
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

    /**
     * Accept a presentation as verifier (by sending a presentation acknowledgement message) to the connection
     * associated with the proof record.
     *
     * @param proofRecordId the id of the proof record for which to accept the presentation.
     * @return proof record associated with the sent presentation acknowledgement message.
     */
//    suspend fun acceptPresentation(proofRecordId: String): ProofExchangeRecord {
//        val record = agent.proofRepository.getById(proofRecordId)
//        val connection = agent.connectionRepository.getById(record.connectionId)
//        val (message, proofRecord) = agent.proofService.createAck(record)
//        Log.d("MAIN_MESSAGE", "acceptPresentation")
//        agent.messageSender.send(OutboundMessage(message, connection))
//        return proofRecord
//    }

    /**
     * Create a [RetrievedCredentials] object. Given input proof request,
     * use credentials in the wallet to build indy requested credentials object for proof creation.
     *
     * @param proofRecordId the id of the proof request to get the matching credentials for.
     * @return [RetrievedCredentials] object.
     */
    suspend fun getRequestedCredentialsForProofRequest(proofRecordId: String): RetrievedCredentialsAnonCreds {
        val record = agent.proofRepository.getById(proofRecordId)

        checkIfMessageTypeIsCorrect(proofRecordId)

        val proofRequestMessageJson = agent.didCommMessageRepository.getAgentMessage(
            record.id,
            RequestPresentationMessageV2.type,
        )

        val proofRequestMessage =
            MessageSerializer.decodeFromString(proofRequestMessageJson) as RequestPresentationMessageV2
        updateProofFormat(record, proofRequestMessage.formats)

        val proofRequestJson = proofRequestMessage.anoncredsProofRequest()
        logger.debug("Proof request json: $proofRequestJson")
        val proofRequest = Json.decodeFromString<AnonCredsProofRequest>(proofRequestJson)

        return agent.proofServiceV2.getRequestedCredentialsForProofRequest(proofRequest)
    }

    private suspend fun checkIfMessageTypeIsCorrect(proofRecordId: String) {
        val recordMessageType =
            agent.didCommMessageRepository.getSingleByQuery("{\"associatedRecordId\": \"$proofRecordId\"}")

        if (!recordMessageType.message.contains("/2.0/")) {
            throw Exception("Version of proof protocol is incorrect")
        }
    }

    private suspend fun updateProofFormat(
        record: ProofExchangeRecord,
        formats: List<ProofFormatSpec>,
    ) {
        record.formats = formats
        agent.proofRepository.update(record)
    }
}
