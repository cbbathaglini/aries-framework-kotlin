package org.hyperledger.ariesframework.credentials.v2.formats

import org.hyperledger.ariesframework.credentials.messages.IOfferCredentialMessage


interface CredentialFormatService{

//    // Proposal methods
//    suspend fun createProposal(
//        options: CredentialFormatCreateProposalOptions<CF>
//    ): CredentialFormatCreateProposalReturn
//
//    suspend fun processProposal(
//        options: CredentialFormatProcessOptions)
//
//    suspend fun acceptProposal(
//        options: CredentialFormatAcceptProposalOptions<CF>
//    ): CredentialFormatCreateOfferReturn
//
//    // Offer methods
//    suspend fun createOffer(
//        options: CredentialFormatCreateOfferOptions<CF>
//    ): CredentialFormatCreateOfferReturn

    suspend fun processOffer(
        options: IOfferCredentialMessage)

//    suspend fun acceptOffer(
//
//        options: CredentialFormatAcceptOfferOptions<CF>
//    ): CredentialFormatCreateReturn
//
//    // Request methods
//    suspend fun createRequest(
//        options: CredentialFormatCreateRequestOptions<CF>
//    ): CredentialFormatCreateReturn
//
//    suspend fun processRequest(options: CredentialFormatProcessOptions)
//
//    suspend fun acceptRequest(
//        options: CredentialFormatAcceptRequestOptions<CF>
//    ): CredentialFormatCreateReturn
//
//    // Credential methods
//    suspend fun processCredential(
//        options: CredentialFormatProcessCredentialOptions)
//
//    // Auto accept methods
//    suspend fun shouldAutoRespondToProposal(
//        options: CredentialFormatAutoRespondProposalOptions
//    ): Boolean
//
//    suspend fun shouldAutoRespondToOffer(
//        options: CredentialFormatAutoRespondOfferOptions
//    ): Boolean
//
//    suspend fun shouldAutoRespondToRequest(
//        options: CredentialFormatAutoRespondRequestOptions
//    ): Boolean
//
//    suspend fun shouldAutoRespondToCredential(
//        options: CredentialFormatAutoRespondCredentialOptions
//    ): Boolean
//
//    suspend fun deleteCredentialById(credentialId: String)
//
//    fun supportsFormat(formatIdentifier: String): Boolean
}