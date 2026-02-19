package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import org.hyperledger.ariesframework.credentials.formats.CredentialFormat
import org.hyperledger.ariesframework.credentials.formats.LinkedAttachment
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute

class AnoncredsCredentialFormat(
    override val formatKey: String = "anoncreds",
    override val credentialRecordType: String = "w3c",
    override val credentialFormats: CredentialFormatAnonCreds,
    override val formatData: FormatDataAnonCreds,

    val credentialDefinitionId: String,
    val revocationRegistryDefinitionId: String? = null,
    val revocationRegistryIndex: Long? = null,
    val attributes: List<CredentialPreviewAttribute>,
    val linkedAttachments: List<LinkedAttachment>? = emptyList(),
    val linkSecretId: String, // [TODO] Added for me
) : CredentialFormat
