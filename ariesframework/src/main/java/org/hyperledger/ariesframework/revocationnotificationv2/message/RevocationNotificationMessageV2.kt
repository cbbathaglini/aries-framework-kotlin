package org.hyperledger.ariesframework.revocationnotificationv2.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.decorators.AckDecorator
import org.hyperledger.ariesframework.revocationnotification.model.RevocationNotificationMessageV1Options
import org.hyperledger.ariesframework.revocationnotificationv2.model.RevocationNotificationMessageV2Options

@Serializable
class RevocationNotificationMessageV2(
    @SerialName("revocation_format")
    val revocationFormat: String,

    @SerialName("credential_id")
    var credentialId: String,

    var comment: String? = null,

    var pleaseAck: AckDecorator? = null
) : AgentMessage(generateId(), RevocationNotificationMessageV2.type) {

    companion object {
        val type = "https://didcomm.org/revocation_notification/2.0/revoke"
    }

    val messageTypeUri: String = type

    constructor(options: RevocationNotificationMessageV2Options) : this(
        revocationFormat = options.revocationFormat,
        credentialId = options.credentialId,
        comment = options.comment,
        pleaseAck = options.pleaseAck
    )

    override fun toString(): String {
        return "RevocationNotificationMessageV2(revocationFormat='$revocationFormat', credentialId='$credentialId', comment=$comment, pleaseAck=$pleaseAck, messageTypeUri='$messageTypeUri')"
    }


}