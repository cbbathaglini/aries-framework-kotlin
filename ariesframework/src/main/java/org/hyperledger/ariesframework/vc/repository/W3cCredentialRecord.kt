package org.hyperledger.ariesframework.vc.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.vc.model.ClaimFormat
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential
import org.hyperledger.ariesframework.vc.model.W3cVerifiableCredential

@Serializable
class W3cCredentialRecord (
    override var id: String,
    override var _tags: Tags?,
    override val createdAt: Instant,
    override var updatedAt: Instant?,
    val credential: W3cVerifiableCredential
) : BaseRecord(){
    companion object {
        const val type = "W3cCredentialRecord"
    }

    constructor(
        tags: Tags? = null,
        credential: W3cVerifiableCredential
    ) : this(
        id = BaseRecord.generateId(),
        _tags = tags,
        createdAt = Clock.System.now(),
        updatedAt = null,
        credential = credential
    ) {
        val tagMap = (tags ?: mutableMapOf()).toMutableMap()
        _tags = tagMap
    }


    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()
        val stringContexts = this.credential.contexts.filter((ctx): ctx is string => typeof ctx === 'string')

        tags.putAll(this._tags) // Supondo que _tags seja um Map<String, Any?>
        tags["issuerId"] = credential.issuerId
        tags["subjectIds"] = credential.credentialSubjectIds
        tags["schemaIds"] = credential.credentialSchemaIds
        tags["contexts"] = stringContexts
        tags["givenId"] = credential.id
        tags["claimFormat"] = credential.claimFormat.name
        tags["types"] = credential.type

        when (credential.claimFormat) {
            ClaimFormat.LdpVc -> {
                tags["proofTypes"] = (credential as? W3cJsonLdVerifiableCredential)?.proofTypes
                tags["cryptosuites"] = (credential as? W3cJsonLdVerifiableCredential)?.dataIntegrityCryptosuites
            }
            ClaimFormat.JwtVc -> {
                tags["algs"] = listOf((credential as? JwtVerifiableCredential)?.jwt?.header?.alg)
            }
        }

        return tags
    }
}