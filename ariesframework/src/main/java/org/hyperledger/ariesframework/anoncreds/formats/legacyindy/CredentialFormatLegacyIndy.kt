package org.hyperledger.ariesframework.anoncreds.formats.legacyindy

import org.hyperledger.ariesframework.anoncreds.formats.CredentialFormatOperations
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsAcceptOfferFormat
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsAcceptProposalFormat
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsAcceptRequestFormat
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsOfferCredentialFormat
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsProposeCredentialFormat

data class CredentialFormatLegacyIndy(
    override val createProposal: LegacyIndyProposeCredentialFormat,
    override val acceptProposal: AnonCredsAcceptProposalFormat,
    override val createOffer: AnonCredsOfferCredentialFormat,
    override val acceptOffer: AnonCredsAcceptOfferFormat,
    override val createRequest: Nothing? = null,
    override val acceptRequest: AnonCredsAcceptRequestFormat
) : CredentialFormatOperations( createProposal, acceptProposal, createOffer, acceptOffer, createRequest, acceptRequest)