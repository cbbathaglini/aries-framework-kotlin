package org.hyperledger.ariesframework.basicmessage.messages

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable


@Serializable
class BasicMessageInfos(
    val content: String,
    val createdAt: Instant? = null,
    val theirLabel: String? = null,
    val connectionRecordId: String? = null,
)
