package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import org.hyperledger.ariesframework.anoncreds.formats.CredentialFormatOperations

data class CredentialFormatAnonCreds(
    override val createProposal: AnonCredsProposeCredentialFormat,
    override val acceptProposal: AnonCredsAcceptProposalFormat,
    override val createOffer: AnonCredsOfferCredentialFormat,
    override val acceptOffer: AnonCredsAcceptOfferFormat,
    override val createRequest: Nothing? = null,
    override val acceptRequest: AnonCredsAcceptRequestFormat,
) : CredentialFormatOperations(createProposal, acceptProposal, createOffer, acceptOffer, createRequest, acceptRequest)
