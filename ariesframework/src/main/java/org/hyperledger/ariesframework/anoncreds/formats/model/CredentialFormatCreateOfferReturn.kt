package org.hyperledger.ariesframework.anoncreds.formats.model

import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute
import org.hyperledger.ariesframework.credentials.v2.models.Format

data class CredentialFormatCreateOfferReturn(
    val attachment: Attachment,
    val format: Format,
    val previewAttributes: List<CredentialPreviewAttribute>,
)
