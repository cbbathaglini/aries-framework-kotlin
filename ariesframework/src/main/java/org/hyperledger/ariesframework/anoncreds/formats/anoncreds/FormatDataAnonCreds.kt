package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredential
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialOffer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequest
import org.hyperledger.ariesframework.credentials.formats.FormatData

data class FormatDataAnonCreds(
    override val proposal: AnonCredsCredentialProposalFormat,
    override val offer: AnonCredsCredentialOffer,
    override val request: AnonCredsCredentialRequest,
    override val credential: AnonCredsCredential,
) : FormatData(proposal, offer, request, credential)
