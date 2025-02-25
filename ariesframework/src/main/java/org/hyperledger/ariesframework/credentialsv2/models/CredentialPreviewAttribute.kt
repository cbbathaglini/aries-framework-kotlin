package org.hyperledger.ariesframework.credentialsv2.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
class CredentialPreviewAttribute (
    var name:String,

    @SerialName("mime-type")
    var mimeType: String? = "text/plain",

    var value:String
){
    constructor(options: CredentialPreviewAttributeOptions) : this (
        name = options.name,
        mimeType = options.mimeType,
        value = options.value,
    )

    fun toJSON(): String {
        return Json.encodeToString(this)
    }
}