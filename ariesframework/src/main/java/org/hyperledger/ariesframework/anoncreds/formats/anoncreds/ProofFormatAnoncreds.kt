package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.Serializable

@Serializable
data class ProofFormatAnoncreds(
    val createProposal: AnonCredsProposeProofFormat,
    val acceptProposal: AnonCredsAcceptProposalProofFormat,
    val createRequest: AnonCredsRequestProofFormat,
    val acceptRequest: AnonCredsSelectedCredentials,
    val getCredentialsForRequest: AnonCredsGetCredentialsForRequest,
    val selectCredentialsForRequest: AnonCredsSelectCredentialsForRequest,
)
