package org.hyperledger.ariesframework.credentials.formats

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.decorators.Attachment

@Serializable
data class LinkedAttachmentOptions(
    val name: String,
    val attachment: Attachment
)