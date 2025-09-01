package org.hyperledger.ariesframework.credentials.models

import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord

data class AcceptProposalParams(
    val credentialRecord: CredentialExchangeRecord,
    val formatServices: List<CredentialFormatService<*>>,
    val comment: String? = null,
    val goal: String? = null,
    val goalCode: String? = null,
    val credentialFormats: Map<String, JsonElement>? = emptyMap(),
)
