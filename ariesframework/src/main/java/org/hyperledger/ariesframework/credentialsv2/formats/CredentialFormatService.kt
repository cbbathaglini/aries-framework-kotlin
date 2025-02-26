package org.hyperledger.ariesframework.credentialsv2.formats

interface CredentialFormatService<CF : CredentialFormat> {
    val formatKey: String
    val credentialRecordType: String

    // Proposal methods
    suspend fun createProposal(
        options: CredentialFormatCreateProposalOptions<CF>
    ): CredentialFormatCreateProposalReturn

    suspend fun processProposal(
        options: CredentialFormatProcessOptions
    )

    suspend fun acceptProposal(
        options: CredentialFormatAcceptProposalOptions<CF>
    ): CredentialFormatCreateOfferReturn

    // Offer methods
    suspend fun createOffer(
        options: CredentialFormatCreateOfferOptions<CF>
    ): CredentialFormatCreateOfferReturn

    suspend fun processOffer(
        options: CredentialFormatProcessOptions
    )

    suspend fun acceptOffer(
        options: CredentialFormatAcceptOfferOptions<CF>
    ): CredentialFormatCreateReturn

    // Request methods
    suspend fun createRequest(
        options: CredentialFormatCreateRequestOptions<CF>
    ): CredentialFormatCreateReturn

    suspend fun processRequest(
        options: CredentialFormatProcessOptions
    )

    suspend fun acceptRequest(
        options: CredentialFormatAcceptRequestOptions<CF>
    ): CredentialFormatCreateReturn

    // Credential methods
    suspend fun processCredential(
        options: CredentialFormatProcessCredentialOptions
    )

    // Auto-accept methods
    suspend fun shouldAutoRespondToProposal(
        options: CredentialFormatAutoRespondProposalOptions
    ): Boolean

    suspend fun shouldAutoRespondToOffer(
        options: CredentialFormatAutoRespondOfferOptions
    ): Boolean

    suspend fun shouldAutoRespondToRequest(
        options: CredentialFormatAutoRespondRequestOptions
    ): Boolean

    suspend fun shouldAutoRespondToCredential(
        options: CredentialFormatAutoRespondCredentialOptions
    ): Boolean

    // Deletion method
    suspend fun deleteCredentialById(
        credentialId: String
    )

    fun supportsFormat(formatIdentifier: String): Boolean
}