package org.hyperledger.ariesframework.credentials.v2.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.credentials.v1.models.CredentialPreviewAttribute
import org.hyperledger.ariesframework.credentials.v2.CredentialsV2Constants.Companion.CREDENTIAL_PREVIEW

@Serializable
class CredentialPreviewV2(
    var attributes: List<CredentialPreviewAttribute>,
) {
    @SerialName("@type")
    val type: String = CREDENTIAL_PREVIEW

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