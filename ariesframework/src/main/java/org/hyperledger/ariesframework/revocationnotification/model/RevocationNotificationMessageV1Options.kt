package org.hyperledger.ariesframework.revocationnotification.model

import org.hyperledger.ariesframework.decorators.AckDecorator


interface RevocationNotificationMessageV1Options {
    val issueThread: String
    val id: String?
    val comment: String?
    val pleaseAck: AckDecorator?
}