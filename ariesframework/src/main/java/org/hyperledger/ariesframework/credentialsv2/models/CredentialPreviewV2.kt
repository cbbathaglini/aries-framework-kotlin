package org.hyperledger.ariesframework.credentialsv2.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
class CredentialPreviewV2(
    var attributes: List<CredentialPreviewAttribute>,
) {
    @SerialName("@type")
    val type: String = "https://didcomm.org/issue-credential/2.0/credential-preview"

    constructor(options: CredentialPreviewOptions) : this(
        attributes = options.attributes.map {
            CredentialPreviewAttribute(it)
        }
    )

    fun toJSON(): String {
        return Json.encodeToString(this)
    }

    companion object {
        fun fromRecord(record: Map<String, String>): CredentialPreviewV2 {
            val attributes = record.map { (name, value) ->
                CredentialPreviewAttribute(name, "text/plain", value)
            }
            return CredentialPreviewV2(attributes)
        }
    }
}