package org.hyperledger.ariesframework.revocationnotification.model

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.decorators.AckDecorator

@Serializable
data class RevocationNotificationMessageV1Options(
    val issueThread: String,
    val comment: String? = null,
    val pleaseAck: AckDecorator? = null
)