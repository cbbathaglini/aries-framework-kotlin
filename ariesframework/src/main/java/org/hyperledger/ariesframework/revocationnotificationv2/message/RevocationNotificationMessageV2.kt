package org.hyperledger.ariesframework.revocationnotificationv2.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.decorators.AckDecorator
import org.hyperledger.ariesframework.decorators.AckValues
import org.hyperledger.ariesframework.revocationnotificationv2.RevocationNotificationConstants

@Serializable
class RevocationNotificationMessageV2(
    @SerialName("revocation_format")
    val revocationFormat: String,

    @SerialName("credential_id")
    var credentialId: String,

    var comment: String? = null,

    var pleaseAck: AckDecorator? = null
) : AgentMessage(generateId(), type) {

    companion object {
        val type = RevocationNotificationConstants.TYPE_MESSAGE
    }

    fun getThreadId(anonCredsRevocationRegistryId: String, anonCredsCredentialRevocationId:String): String {
        return "indy::${anonCredsRevocationRegistryId}::${anonCredsCredentialRevocationId}"
    }

    fun setPleaseAck(on: List<AckValues> = listOf(AckValues.Receipt)) {
        this.pleaseAck = AckDecorator(on)
    }

    fun pleaseAckIsEmpty(): Boolean {
        val empty = this.pleaseAck?.isNotEmpty() ?: true
        return empty
    }
}