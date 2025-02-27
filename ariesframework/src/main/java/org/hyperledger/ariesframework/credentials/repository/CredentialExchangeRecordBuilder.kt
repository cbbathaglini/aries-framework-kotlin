package org.hyperledger.ariesframework.credentials.repository

import kotlinx.datetime.Instant
import org.hyperledger.ariesframework.credentials.models.CredentialRecordType
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.models.CredentialRole
import org.hyperledger.ariesframework.revocationnotification.model.RevocationNotification

class CredentialExchangeRecordBuilder {
    private var id: String? = null
    private var _tags: Map<String, String>? = null
    private var createdAt: Instant? = null
    private var updatedAt: Instant? = null
    private var connectionId: String? = null
    private var threadId: String? = null
    private var state: CredentialState? = null
    private var protocolVersion: String? = null
    private var credentialDefinitionId: String? = null
    private var revocationNotification: RevocationNotification? = null
    private var role: CredentialRole? = null
    private var credentials: MutableList<CredentialRecordBinding> = mutableListOf()

    fun id(id: String) = apply { this.id = id }

    fun tags(tags: Map<String, String>) = apply { this._tags = tags }

    fun createdAt(createdAt: Instant) = apply { this.createdAt = createdAt }

    fun updatedAt(updatedAt: Instant?) = apply { this.updatedAt = updatedAt }

    fun connectionId(connectionId: String) = apply { this.connectionId = connectionId }

    fun threadId(threadId: String) = apply { this.threadId = threadId }

    fun revokedState() = apply { this.state = CredentialState.Revoked }

    fun state(state: CredentialState) = apply { this.state = state }

    fun protocolVersion(protocolVersion: String) = apply { this.protocolVersion = protocolVersion }

    fun credentialDefinitionId(credentialDefinitionId: String) = apply { this.credentialDefinitionId = credentialDefinitionId }

    fun revocationNotification(revocationNotification: RevocationNotification?) = apply { this.revocationNotification = revocationNotification }

    fun role(role: CredentialRole?) = apply { this.role = role }

    fun holderRole() = apply { this.role = CredentialRole.Holder }

    //fun credentials(credentials: MutableList<CredentialRecordBinding>) = apply { this.credentials = credentials }

    fun build(): CredentialExchangeRecord {
        return CredentialExchangeRecord(
            id = id ?: throw IllegalStateException("id is required"),
            _tags = _tags ?: emptyMap(),
            createdAt = createdAt ?: throw IllegalStateException("createdAt is required"),
            updatedAt = updatedAt,
            connectionId = connectionId ?: throw IllegalStateException("connectionId is required"),
            threadId = threadId ?: throw IllegalStateException("threadId is required"),
            state = state ?: throw IllegalStateException("state is required"),
            protocolVersion = protocolVersion ?: throw IllegalStateException("protocolVersion is required"),
            credentialDefinitionId = credentialDefinitionId,
            revocationNotification = revocationNotification,
            credentials = mutableListOf(CredentialRecordBinding(credentialRecordType = CredentialRecordType.Indy, credentialRecordId = id!!)),
            role = role
        )
    }
}