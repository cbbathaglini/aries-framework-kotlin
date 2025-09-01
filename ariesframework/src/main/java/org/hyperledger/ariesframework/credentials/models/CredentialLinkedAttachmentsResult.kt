package org.hyperledger.ariesframework.credentials.models

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.decorators.Attachment

@Serializable
data class CredentialLinkedAttachmentsResult(
    val attachments: List<Attachment>? = null,
    val previewAttributes: List<CredentialPreviewAttribute>? = null,
)
