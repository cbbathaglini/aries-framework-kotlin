package org.hyperledger.ariesframework.credentials.modelv2

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute

@Serializable
data class CredentialLinkedAttachmentsResult(
    val attachments: List<Attachment>? = null,
    val previewAttributes: List<CredentialPreviewAttribute>? = null
)