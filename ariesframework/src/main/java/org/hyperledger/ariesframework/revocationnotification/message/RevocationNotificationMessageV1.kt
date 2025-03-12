package org.hyperledger.ariesframework.revocationnotification.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.decorators.AckDecorator
import org.hyperledger.ariesframework.revocationnotification.model.RevocationNotificationMessageV1Options

@Serializable
class RevocationNotificationMessageV1(
    @SerialName("thread_id")
    val issueThread: String,
    var comment: String? = null,
    var pleaseAck: AckDecorator? = null
) : AgentMessage(generateId(), RevocationNotificationMessageV1.type) {

    companion object {
        val type = "https://didcomm.org/revocation_notification/1.0/revoke"
    }

    val messageTypeUri: String = type

    constructor(options: RevocationNotificationMessageV1Options) : this(
        issueThread = options.issueThread,
        comment = options.comment,
        pleaseAck = options.pleaseAck
    )

}