package org.hyperledger.ariesframework.proofs.formats

import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsCredentialsForProofRequest
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsSelectedCredentials
import org.hyperledger.ariesframework.proofs.messages.v2.RequestPresentationMessageV2
import org.hyperledger.ariesframework.proofs.models.ProofFormatCreateReturn
import org.hyperledger.ariesframework.proofs.models.ProofFormatProcessOptions
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

interface ProofFormatService<CF : ProofFormat> {
    val formatKey: String

    suspend fun createProposal(
        profRecord: ProofExchangeRecord,
        attachmentId: String? = null,
        proofFormats: Map<String, JsonElement> = emptyMap(),
    ): ProofFormatCreateReturn

    suspend fun processProposal(
        attachment: Attachment,
        proofRecord: ProofExchangeRecord,
    )

    suspend fun acceptProposal(
        proofRecord: ProofExchangeRecord,
        attachmentId: String? = null,
        proposalAttachment: Attachment,
        proofFormats: Map<String, JsonElement>? = emptyMap(),
    ): ProofFormatCreateReturn

    suspend fun createRequest(
        proofRecord: ProofExchangeRecord,
        attachmentId: String? = null,
        proofFormats: Map<String, JsonElement>? = emptyMap(),
    ): ProofFormatCreateReturn

    suspend fun processRequest(
        options: ProofFormatProcessOptions,
    )

    suspend fun acceptRequest(
        requestMessage: RequestPresentationMessageV2,
        proofRecord: ProofExchangeRecord,
        proofFormats: Map<String, JsonElement>?,
        attachmentId: String,
        requestAttachment: Attachment,
        proposalAttachment: Attachment? = null,
    ): ProofFormatCreateReturn

    suspend fun processPresentation(
        requestAttachment: Attachment,
        attachment: Attachment,
        proofRecord: ProofExchangeRecord,
    ): Boolean

    suspend fun getCredentialsForRequest(
        proofRecord: ProofExchangeRecord,
        proofFormats: Map<String, JsonElement>? = emptyMap(),
        requestAttachment: Attachment,
        proposalAttachment: Attachment?,
    ): AnonCredsCredentialsForProofRequest

    suspend fun selectCredentialsForRequest(
        proofRecord: ProofExchangeRecord,
        proofFormats: Map<String, JsonElement>? = emptyMap(),
        requestAttachment: Attachment,
        proposalAttachment: Attachment? = null,
    ): AnonCredsSelectedCredentials

    suspend fun shouldAutoRespondToProposal(
        proofRecord: ProofExchangeRecord,
        proposalAttachment: Attachment,
        requestAttachment: Attachment,
    ): Boolean

    suspend fun shouldAutoRespondToRequest(
        proofRecord: ProofExchangeRecord,
        requestAttachment: Attachment,
        proposalAttachment: Attachment,
    ): Boolean

    suspend fun shouldAutoRespondToPresentation(
        proofRecord: ProofExchangeRecord,
        proposalAttachment: Attachment?,
        requestAttachment: Attachment,
        presentationAttachment: Attachment,
    ): Boolean

    fun supportsFormat(formatIdentifier: String): Boolean
}
