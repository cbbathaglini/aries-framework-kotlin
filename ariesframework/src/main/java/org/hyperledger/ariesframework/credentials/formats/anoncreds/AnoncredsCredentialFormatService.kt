package org.hyperledger.ariesframework.credentials.formats.anoncreds

import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.credentials.formats.CredentialFormat
import org.hyperledger.ariesframework.credentials.models.AcceptCredentialOptions
import org.hyperledger.ariesframework.credentials.models.AcceptOfferOptions
import org.hyperledger.ariesframework.credentials.models.AcceptRequestOptions
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.messages.CredentialAckMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.IssueCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.OfferCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.ProposeCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.messages.RequestCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.models.CreateCredentialOfferOptionsV2
import org.hyperledger.ariesframework.credentials.v2.models.CreateProposalOptionsV2
import org.hyperledger.ariesframework.problemreports.messages.CredentialProblemReportNotificationMessage

class AnoncredsCredentialFormatService : CredentialFormat{

    companion object {
        const val ANONCREDS_CREDENTIAL_OFFER = "anoncreds/credential-offer@v1.0"
        const val ANONCREDS_CREDENTIAL_REQUEST = "anoncreds/credential-request@v1.0"
        const val ANONCREDS_CREDENTIAL_FILTER = "anoncreds/credential-filter@v1.0"
        const val ANONCREDS_CREDENTIAL = "anoncreds/credential@v1.0"
    }


    override suspend fun createProposeCredentialMessage(options: CreateProposalOptionsV2): Pair<ProposeCredentialMessageV2, CredentialExchangeRecord> {
        TODO("Not yet implemented")
    }

    override suspend fun createOfferCredentialMessage(options: CreateCredentialOfferOptionsV2): Pair<OfferCredentialMessageV2, CredentialExchangeRecord> {
        TODO("Not yet implemented")
    }

    override suspend fun createRequestCredentialMessage(options: AcceptOfferOptions): RequestCredentialMessageV2 {
        TODO("Not yet implemented")
    }

    override suspend fun processRequestCredentialMessage(messageContext: InboundMessageContext): CredentialExchangeRecord {
        TODO("Not yet implemented")
    }

    override suspend fun processOfferCredentialMessage(messageContext: InboundMessageContext): CredentialExchangeRecord {
        TODO("Not yet implemented")
    }

    override suspend fun processIssueCredentialMessage(messageContext: InboundMessageContext): CredentialExchangeRecord {
        TODO("Not yet implemented")
    }

    override suspend fun createCredentialAckMessage(options: AcceptCredentialOptions): CredentialAckMessageV2 {
        TODO("Not yet implemented")
    }

    override suspend fun createIssueCredentialMessage(options: AcceptRequestOptions): IssueCredentialMessageV2 {
        TODO("Not yet implemented")
    }

    override suspend fun createOfferDeclinedProblemReport(options: AcceptOfferOptions): CredentialProblemReportNotificationMessage {
        TODO("Not yet implemented")
    }

    override suspend fun updateState(
        credentialRecord: CredentialExchangeRecord,
        newState: CredentialState
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun processAck(messageContext: InboundMessageContext): CredentialExchangeRecord {
        TODO("Not yet implemented")
    }

    override suspend fun getHolderDid(credentialRecord: CredentialExchangeRecord): String {
        TODO("Not yet implemented")
    }

    override suspend fun supportsFormat(format: String): Boolean {
        val supportedFormats = listOf(
            ANONCREDS_CREDENTIAL_REQUEST,
            ANONCREDS_CREDENTIAL_OFFER,
            ANONCREDS_CREDENTIAL_FILTER,
            ANONCREDS_CREDENTIAL
        )
        return supportedFormats.contains(format)
    }
}