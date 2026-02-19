package org.hyperledger.ariesframework.anoncreds.formats.legacyindy

import kotlinx.serialization.SerialName
import org.hyperledger.ariesframework.credentials.formats.CredentialFormat
import org.hyperledger.ariesframework.credentials.formats.LinkedAttachment
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute

class LegacyIndyCredentialFormat(
    @SerialName("formatKey")
    override val formatKey: String = "indy",

    @SerialName("credentialRecordType")
    override val credentialRecordType: String = "w3c",

    @SerialName("credentialFormats")
    override val credentialFormats: CredentialFormatLegacyIndy,

    @SerialName("formatData")
    override val formatData: FormatDataLegacyIndy,

    // [TODO]added to me, understand
    val credentialDefinitionId: String,
    val revocationRegistryDefinitionId: String? = null,
    val revocationRegistryIndex: Long? = null,
    val attributes: List<CredentialPreviewAttribute>,
    val linkedAttachments: List<LinkedAttachment>? = emptyList(),
    val linkSecretId: String, // [TODO] Added for me
) : CredentialFormat
