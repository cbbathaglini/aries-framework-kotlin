package org.hyperledger.ariesframework.vc.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.vc.model.ClaimFormat
import org.hyperledger.ariesframework.vc.model.W3cCredential
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential
import org.hyperledger.ariesframework.vc.model.W3cVerifiableCredential

@Serializable
class W3cCredentialRecord (
    override var id: String,
    override var _tags: Tags?,
    override val createdAt: Instant,
    override var updatedAt: Instant?,
    val credential: W3cCredential
) : BaseRecord(){

    companion object {
        const val type = "W3cCredentialRecord"
    }

    constructor(
        tags: Tags? = null,
        credential: W3cCredential
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
        //val stringContexts = this.credential.contexts.filter((ctx): ctx is string => typeof ctx === 'string')

        tags.putAll(this._tags ?: emptyMap())
        if(credential is W3cJsonLdVerifiableCredential){
            tags["issuerId"] = credential.issuer.toString()
            tags["subjectIds"] = credential.credentialSubject.toString()
            tags["schemaIds"] = credential.credentialSchemaIds.toString()
            tags["contexts"] = credential.contexts.filterIsInstance<String>().toString()
            tags["givenId"] = credential.id.toString()
            tags["claimFormat"] = credential.claimFormat
            tags["types"] = credential.type.toString()
            tags["proofTypes"] = credential.proofTypes.toString()
            tags["cryptosuites"] = credential.dataIntegrityCryptosuites.toString()
        }

//            is W3cJwtVerifiableCredential -> {
//                tags["issuerId"] = credential.issuerId
//                tags["subjectIds"] = credential.credentialSubjectIds
//                tags["schemaIds"] = credential.credentialSchemaIds
//                tags["contexts"] = credential.contexts.filterIsInstance<String>()
//                tags["givenId"] = credential.id
//                tags["claimFormat"] = credential.claimFormat.name
//                tags["types"] = credential.type
//                tags["algs"] = listOfNotNull(credential.jwt.header.alg)
//            }


//        tags["issuerId"] = credential.issuerId
//        tags["subjectIds"] = credential.credentialSubjectIds
//        tags["schemaIds"] = credential.credentialSchemaIds
//        tags["contexts"] = stringContexts
//        tags["givenId"] = credential.id
//        tags["claimFormat"] = credential.claimFormat.name
//        tags["types"] = credential.type
//        tags["algs"] = listOf((credential as? JwtVerifiableCredential)?.jwt?.header?.alg)


        return tags
    }
}