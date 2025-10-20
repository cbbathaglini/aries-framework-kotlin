package org.hyperledger.ariesproject

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class NotificationItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val date: Long = System.currentTimeMillis(),
    var isRead: Boolean = false,
    val type: NotificationType = NotificationType.OTHER,
    val credentialId: String? = null,
    val proofRecordId: String? = null,
    val presentationMessageId: String? = null,
    val connectionId: String? = null
)

@Serializable
enum class NotificationType {
    ISSUE_CREDENTIAL_V1,
    ISSUE_CREDENTIAL_V2,
    ISSUED_CREDENTIAL_DETAIL_V1,
    ISSUED_CREDENTIAL_DETAIL_V2,
    ACCEPT_PROOF_REQUEST_V1,
    ACCEPT_PROOF_REQUEST_V2,
    PROOF_REQUEST_V1,
    PROOF_REQUEST_V2,
    PRESENTATION_PROOF_V1,
    PRESENTATION_PROOF_V2,
    SEND_BLUETOOTH_PRESENTATION_PROOF_V2,
    RECEIVED_BLUETOOTH_PRESENTATION_PROOF_V2,
    PROOF_V2,
    CONNECTION,
    ERROR,
    OTHER;

    companion object {
        fun fromString(value: String?): NotificationType {
            return try {
                value?.let { valueOf(it.uppercase()) } ?: OTHER
            } catch (e: IllegalArgumentException) {
                OTHER
            }
        }
    }
}