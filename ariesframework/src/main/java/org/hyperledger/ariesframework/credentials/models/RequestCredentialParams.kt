package org.hyperledger.ariesframework.credentials.models

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.models.Format

@Serializable
data class RequestCredentialParams(
    val credentialFormats : List<Format>, //Map<String, JsonElement>,
    val formatServices : List<CredentialFormatService<*>>,
    val credentialRecord: CredentialExchangeRecord,
    val comment: String?,
    val goalCode: String?,
    val goal: String?
)