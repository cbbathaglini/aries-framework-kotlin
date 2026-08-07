package org.hyperledger.ariesframework.vc.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.vc.model.W3cCredential

@Serializable
class W3cCredentialRecord(
    override var id: String,
    override var _tags: Tags?,
    override val createdAt: Instant,
    override var updatedAt: Instant?,
    val credential: W3cCredential,
) : BaseRecord() {

    companion object {
        const val type = "W3cCredentialRecord"
    }

    constructor(
        tags: Tags? = null,
        credential: W3cCredential,
    ) : this(
        id = BaseRecord.generateId(),
        _tags = tags,
        createdAt = Clock.System.now(),
        updatedAt = null,
        credential = credential,
    ) {
        val tagMap = (tags ?: mutableMapOf()).toMutableMap()
        _tags = tagMap
    }

    fun getTagsW3cJsonLd(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()
        tags.putAll(this._tags ?: emptyMap())
        tags["issuerId"] = credential.issuer.toString()
        tags["subjectIds"] = credential.credentialSubject.toString()
        // make it work
//            tags["schemaIds"] = credential.credentialSchemaIds.toString()
//            tags["contexts"] = credential.contexts.filterIsInstance<String>().toString()
        tags["givenId"] = credential.id.toString()
        // tags["claimFormat"] = credential.claimFormat
        tags["types"] = credential.type.toString()
        // tags["proofTypes"] = credential.proofTypes.toString()
        // tags["cryptosuites"] = credential.dataIntegrityCryptosuites.toString()
        return tags
    }

    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()

        val subjectIds = credential.credentialSubject
            .mapNotNull { it.id?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        if (subjectIds.isNotEmpty()) {
            tags["subjectId"] = subjectIds.first()
            tags["subjectIds"] = subjectIds.joinToString(",") // list case
        } else {
            tags["subjectId"] = ""
            tags["subjectIds"] = ""
        }

        credential.id?.let { tags["givenId"] = it }
        tags["issuerId"] = credential.issuer.toString()
        tags["types"] = credential.type.joinToString(",")

        subjectIds.forEach { sid ->
            tags["subjectId:$sid"] = "1"
        }

        _tags = tags
        return tags
    }

    fun getTagsAux(): Tags {
        return this._tags!!
    }

    override fun toString(): String {
        return "W3cCredentialRecord(id='$id', _tags=$_tags, createdAt=$createdAt, updatedAt=$updatedAt, credential=$credential)"
    }
}
