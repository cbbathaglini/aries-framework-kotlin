package org.hyperledger.ariesframework.anoncreds.formats.legacyindy

import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredential
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialOffer
import org.hyperledger.ariesframework.credentials.formats.FormatData

data class FormatDataLegacyIndy(
    override val proposal: LegacyIndyCredentialProposalFormat,
    override val offer: AnonCredsCredentialOffer,
    override val request: LegacyIndyCredentialRequest,
    override val credential: AnonCredsCredential,
) : FormatData(proposal, offer, request, credential)
