package org.hyperledger.ariesframework.anoncreds.formats

import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.messages.IOfferCredentialMessage
import org.hyperledger.ariesframework.credentials.v1.CredentialService
import org.hyperledger.ariesframework.credentials.v2.formats.CredentialFormatService
import org.slf4j.LoggerFactory


class IndyCredentialFormatService : CredentialFormatService{
    companion object {
        const val INDY_CRED_ABSTRACT  = "hlindy/cred-abstract@v2.0"
        const val INDY_CRED_REQUEST = "hlindy/cred-req@v2.0"
        const val INDY_CRED_FILTER = "hlindy/cred-filter@v2.0"
        const val INDY_CRED = "hlindy/cred@v2.0"
    }
    private val logger = LoggerFactory.getLogger(IndyCredentialFormatService::class.java)

    override suspend fun processOffer(
        options: IOfferCredentialMessage
    ) {
        logger.debug("Processing indy credential offer for credential record ${options.credentialRecord.id}")

        val credOffer: AnonCredsCredentialOffer = options.attachment.getDataAsString()

        if (!isUnqualifiedSchemaId(credOffer.schema_id) || !isUnqualifiedCredentialDefinitionId(credOffer.cred_def_id)) {
            throw ProblemReportError("Invalid credential offer", problemCode = CredentialProblemReportReason.IssuanceAbandoned)
        }
    }
}