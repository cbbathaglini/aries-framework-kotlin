package org.hyperledger.ariesframework.anoncreds.formats.model

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute
import org.hyperledger.ariesframework.credentials.v2.models.Format

@Serializable
data class CredentialFormatCreateProposalReturn(
    val format: Format,
    val attachment: Attachment,
    val appendAttachment: Attachment? = null,
    val previewAttribute: List<CredentialPreviewAttribute>? = emptyList(),
)
