package org.hyperledger.ariesframework.proofs.v2

import android.util.Log
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.AckStatus
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
        formats: List<ProofFormatSpec> = emptyList(),
        autoAcceptProof: AutoAcceptProof? = null,
        willConfirm: Boolean? = null,
        comment: String? = null,
    ): ProofExchangeRecord {
        val connection = agent.connectionRepository.getById(connectionId)

        val format: String = formats.first().attachmentId ?: throw CredoError("Formato de prova não informado")

        val proofFormats: Map<String, JsonElement> = ProofUtils.getProofFormats(proofRequest, format)
        logger.info("proof formats: $proofFormats")
        logger.info("proof request >>> ${proofRequest.toJson()}")
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

        Log.d("MAIN_MESSAGE", "requestProof")
        agent.messageSender.send(OutboundMessage(message, connection))

        return record
    }

    /**
     * Cria uma requisição de prova offline (sem conexão) e salva o VerifierRecord.
     *
     * Equivalente a `requestProofOffline` no código Swift.
     */
    suspend fun requestProofOffline(
        proofRequest: AnonCredsProofRequest,
        formats: List<ProofFormatSpec> = emptyList(),
        autoAcceptProof: AutoAcceptProof? = null,
        willConfirm: Boolean? = null,
        comment: String? = null,
    ): Pair<ProofExchangeRecord, VerifierRecord> {
        try {
            // Verifica se há formato informado
            val format = formats.firstOrNull()?.attachmentId
                ?: throw Exception("Proof format not informed")

            // Gera os formatos de prova usando utilitário
            val proofFormats = ProofUtils.getProofFormats(
                proofRequest = proofRequest,
                format = format,
            )

            logger.info("proof formats: $proofFormats")

            // Cria o pedido de prova localmente (sem conexão)
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

            // Cria e salva o VerifierRecord
            val verifierRecord = VerifierRecord(
                proofRequest = proofRequest,
                requestMessage = message,
                globalThreadId = record.threadId,
            )

            agent.verifierRepository.save(verifierRecord)

            return Pair(record, verifierRecord)
        } catch (e: Exception) {
            logger.error("Erro ao criar prova offline: ${e.message}", e)
            throw e
        }
    }

    suspend fun processRequest(
        requestMessage: RequestPresentationMessageV2,
    ): ProofExchangeRecord {
        return agent.proofServiceV2.processRequest(requestMessage = requestMessage)
    }


    suspend fun processPresentationOffline(presentationMessage: String): Pair<ProofExchangeRecord,Boolean> {

        val message = MessageSerializer.decodeFromString(presentationMessage) as? PresentationMessageV2
            ?: throw Exception("Failed to decode PresentationMessageV2")

        val proofRecord = agent.proofServiceV2.processPresentationOffline(message)
            ?: throw Exception("Failed to process presentation")

        val result = proofRecord.isVerified ?: false
        return Pair(proofRecord, result)
    }

    suspend fun processOfflineAck(proofRecord: ProofExchangeRecord){
       agent.proofServiceV2.processOfflineAck(proofRecord)
    }

    suspend fun createPresentation(
        record: ProofExchangeRecord,
        chosenCredentialId: String? = null
    ): Pair<ProofExchangeRecord, PresentationMessageV2> {

        var chosenCredential : CredentialExchangeRecord? = null
        if (chosenCredentialId != null) {
            chosenCredential =
                agent.credentialExchangeRepository.getById(chosenCredentialId)
        }
        logger.info("Chosen credential: ${chosenCredential?.w3cCredentialId ?: "none credential"}")

        val retrievedCredentials = ProofUtils.getRequestedCredentialsForProofRequest(
            proofRecordId = record.id,
            agent = agent,
            credentialW3cId = chosenCredential?.w3cCredentialId
        )

        val requestedCredentials: RequestedCredentialsAnoncreds = agent.proofServiceV2.autoSelectCredentialsForProofRequest(retrievedCredentials)

        val params = AcceptProofRequestOptions(
            proofRecord = record,
            proofFormats = record.formats ?: emptyList(),
            requestedCredentials = requestedCredentials.toMap(),
        )

        val (message, _) = agent.proofServiceV2.acceptRequest(params)
        return Pair(record, message)
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
        chosenCredentialId: String? = null,
        comment: String? = null,
    ): ProofExchangeRecord {

        var chosenCredential : CredentialExchangeRecord? = null
        if (chosenCredentialId != null) {
            chosenCredential =
                agent.credentialExchangeRepository.getById(chosenCredentialId)
        }

        val retrievedCredentials: RetrievedCredentialsAnonCreds =
            ProofUtils.getRequestedCredentialsForProofRequest(
                proofRecordId = proofRecordId,
                agent = agent,
                credentialW3cId = chosenCredential?.w3cCredentialId
            )

        val requestedCredentials: RequestedCredentialsAnoncreds = agent.proofServiceV2.autoSelectCredentialsForProofRequest(retrievedCredentials)


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
            chosenCredentialId= chosenCredentialId
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
}
