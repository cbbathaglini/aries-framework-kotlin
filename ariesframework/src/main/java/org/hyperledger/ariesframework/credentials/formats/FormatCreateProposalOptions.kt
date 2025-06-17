package org.hyperledger.ariesframework.credentials.formats

import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord

data class FormatCreateProposalOptions(
    val credentialFormats: Map<String, JsonElement>,
    val credentialRecord: CredentialExchangeRecord
)