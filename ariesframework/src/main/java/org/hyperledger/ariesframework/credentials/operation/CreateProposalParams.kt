package org.hyperledger.ariesframework.credentials.operation

import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord

data class CreateProposalParams(
    val credentialFormats: Map<String, JsonElement>, // ou CredentialFormatPayload se você tipar melhor
    val formatServices: List<CredentialFormatService<*>>,
    val credentialRecord: CredentialExchangeRecord,
    val comment: String? = null,
    val goalCode: String? = null,
    val goal: String? = null,
)
