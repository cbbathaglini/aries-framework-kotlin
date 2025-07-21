package org.hyperledger.ariesframework.anoncreds.formats.model

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.v2.models.Format

@Serializable
data class CredentialFormatCreateReturn (
    val attachment: Attachment,
    val format: Format,
    val appendAttachment: List<Attachment>? = emptyList()
)
