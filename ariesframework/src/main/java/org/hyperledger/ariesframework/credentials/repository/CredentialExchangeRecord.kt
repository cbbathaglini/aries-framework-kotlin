package org.hyperledger.ariesframework.credentials.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.credentials.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute
import org.hyperledger.ariesframework.credentials.models.CredentialRecordType
import org.hyperledger.ariesframework.credentials.models.CredentialRole
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.models.IndyCredentialView
import org.hyperledger.ariesframework.revocationnotification.model.RevocationNotification
import org.hyperledger.ariesframework.storage.BaseRecord

@Serializable
data class CredentialRecordBinding(
    val credentialRecordType: CredentialRecordType,
    val credentialRecordId: String,
)

@Serializable
data class CredentialExchangeRecord(
    @EncodeDefault
    override var id: String = generateId(),
    override var _tags: Tags? = null,
    @EncodeDefault
    override val createdAt: Instant = Clock.System.now(),
    override var updatedAt: Instant? = null,

    var connectionId: String,
    var threadId: String,
    var state: CredentialState,
    var autoAcceptCredential: AutoAcceptCredential? = null,
    var errorMessage: String? = null,
    var protocolVersion: String,
    var credentials: MutableList<CredentialRecordBinding> = mutableListOf(),
    var credentialAttributes: List<CredentialPreviewAttribute>? = null,
    var indyRequestMetadata: String? = null,
    var credentialDefinitionId: String? = null,
    var role: CredentialRole?  = null,
    var revocationNotification: RevocationNotification? = null
) : BaseRecord() {
    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()

        tags["connectionId"] = connectionId
        tags["threadId"] = threadId
        tags["state"] = state.name

        return tags
    }

    fun getCredentialInfo(): IndyCredentialView? {
        if (credentialAttributes == null) {
            return null
        }

        val claims = credentialAttributes!!.associate { it.name to it.value }

        return IndyCredentialView(claims)
    }

    fun assertProtocolVersion(version: String) {
        if (this.protocolVersion != version) {
            throw Exception("Credential record has invalid protocol version ${this.protocolVersion}. Expected version $version")
        }
    }

    fun assertState(vararg expectedStates: CredentialState) {
        if (!expectedStates.contains(this.state)) {
            throw Exception("Credential record is in invalid state ${this.state}. Valid states are: $expectedStates")
        }
    }

    fun assertRole(vararg expectedRoles: CredentialRole) {
        if (!expectedRoles.contains(this.role)) {
            throw Exception("Credential record is in invalid role ${this.role}. Valid roles are: $expectedRoles")
        }
    }

    fun assertConnection(currentConnectionId: String) {
        if (this.connectionId != currentConnectionId) {
            throw Exception(
                "Credential record is associated with connection '${this.connectionId}'." +
                    " Current connection is '$currentConnectionId'",
            )
        }
    }
}
