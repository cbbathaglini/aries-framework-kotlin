package org.hyperledger.ariesframework.revocationnotification.model

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.util.DateSerializer
import java.util.Date

@Serializable
class RevocationNotification(
    val comment: String? = null,

    @Serializable(with = DateSerializer::class)
    val revocationDate: Date = Date(),
)
