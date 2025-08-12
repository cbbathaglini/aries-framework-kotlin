package org.hyperledger.ariesframework.proofs.formats

import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.proofs.models.FormatCreateRequestOptions
import org.hyperledger.ariesframework.proofs.models.ProofFormatAcceptProposalOptions
import org.hyperledger.ariesframework.proofs.models.ProofFormatAcceptRequestOptions
import org.hyperledger.ariesframework.proofs.models.ProofFormatCreateProposalOptions
import org.hyperledger.ariesframework.proofs.models.ProofFormatCreateReturn
import org.hyperledger.ariesframework.proofs.models.ProofFormatProcessOptions
import org.hyperledger.ariesframework.proofs.models.ProofFormatSelectCredentialsForRequestReturn
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord


interface ProofFormatService<CF : ProofFormat> {
    val formatKey: String

    suspend fun createProposal(
        proofFormatCreateProposalOptions: ProofFormatCreateProposalOptions
    ): ProofFormatCreateReturn

    // proposal methods
    suspend fun processProposal(
        options: ProofFormatProcessOptions
    )

    suspend fun acceptProposal(
        options: ProofFormatAcceptProposalOptions
    ): ProofFormatCreateReturn

    // request methods
    suspend fun createRequest(
        options: FormatCreateRequestOptions
    ): ProofFormatCreateReturn

    suspend fun processRequest(
        options: ProofFormatProcessOptions
    )

    suspend fun acceptRequest(
        options: ProofFormatAcceptRequestOptions
    ): ProofFormatCreateReturn

    // presentation methods
    suspend fun processPresentation(
        requestAttachment: Attachment
    ): Boolean

    // credentials for request
//    suspend fun getCredentialsForRequest(
//        proofRecord: ProofExchangeRecord,
//        proofFormats: Map<String, JsonElement>? = emptyMap(),
//        requestAttachment: Attachment,
//        proposalAttachment: Attachment?
//    ): ProofFormatGetCredentialsForRequestReturn

    suspend fun selectCredentialsForRequest(
        proofRecord: ProofExchangeRecord,
        proofFormats: Map<String, JsonElement>? = emptyMap(),
        requestAttachment: Attachment,
        proposalAttachment: Attachment? = null
    ): ProofFormatSelectCredentialsForRequestReturn

    // auto accept methods
    suspend fun shouldAutoRespondToProposal(
        proofRecord: ProofExchangeRecord,
        proposalAttachment: Attachment,
        requestAttachment: Attachment
    ): Boolean

    suspend fun shouldAutoRespondToRequest(
        proofRecord: ProofExchangeRecord,
        requestAttachment: Attachment,
        proposalAttachment: Attachment
    ): Boolean

    suspend fun shouldAutoRespondToPresentation(
        proofRecord: ProofExchangeRecord,
        proposalAttachment: Attachment?,
        requestAttachment: Attachment,
        presentationAttachment: Attachment
    ): Boolean

    fun supportsFormat(formatIdentifier: String): Boolean
}