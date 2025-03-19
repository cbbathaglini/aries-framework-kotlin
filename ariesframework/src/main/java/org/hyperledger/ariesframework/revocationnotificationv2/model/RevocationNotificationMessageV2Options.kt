package org.hyperledger.ariesframework.revocationnotificationv2.model

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.decorators.AckDecorator

@Serializable
data class RevocationNotificationMessageV2Options(
    val credentialId: String,
    val revocationFormat: String,
    val comment: String? = null,
    val pleaseAck: AckDecorator? = null
){

}