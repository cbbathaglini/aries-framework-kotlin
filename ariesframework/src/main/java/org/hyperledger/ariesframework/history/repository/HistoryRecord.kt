package org.hyperledger.ariesframework.history.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute
import org.hyperledger.ariesframework.credentials.repository.CredentialRecordBinding
import org.hyperledger.ariesframework.proofs.models.RequestedCredentials
import org.hyperledger.ariesframework.storage.BaseRecord

@Serializable
data class HistoryRecord(
    @EncodeDefault
    override var id: String = generateId(),
    override var _tags: Tags? = null,
    @EncodeDefault
    override val createdAt: Instant = Clock.System.now(),
    override var updatedAt: Instant? = null,
    var historyType: String, // from history type
    var connectionId: String,
    var associatedRecordId: String,
    var theirLabel: String? = null,
    var content: String? = null,
    var credentials: MutableList<CredentialRecordBinding>? = null,
    var credentialPreviewAttr: List<CredentialPreviewAttribute>? = null,
    var proofRequestedCredentials: RequestedCredentials? = null,
    // var proofRequestedCredentialsAnoncreds: RequestedCredentialsAnoncreds? = null,
) : BaseRecord() {
    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()

        tags["historyType"] = historyType
        tags["connectionId"] = connectionId
        tags["associatedRecordId"] = associatedRecordId

        if (proofRequestedCredentials != null) {
            for ((_, attr) in proofRequestedCredentials!!.requestedAttributes) {
                tags["credIdAttr:${attr.credentialId}"] = "true"
            }
            for ((_, pred) in proofRequestedCredentials!!.requestedPredicates) {
                tags["credIdPred:${pred.credentialId}"] = "true"
            }
        }

        if (!credentials.isNullOrEmpty()) {
            for (credential in credentials!!) {
                tags["credRecordId:${credential.credentialRecordId}"] = "true"
            }
        }

        return tags
    }

    override fun toString(): String {
        return "HistoryRecord(id=\"$id\", tags=${getTags()}, createdAt=$createdAt, theirLabel=\"$theirLabel\", historyType=\"$historyType\", connectionId=\"$connectionId\", associatedRecordId=\"$associatedRecordId\", credentialPreviewAttr=$credentialPreviewAttr, proofRequestedCredentials=$proofRequestedCredentials, content='$content')"
    }
}
