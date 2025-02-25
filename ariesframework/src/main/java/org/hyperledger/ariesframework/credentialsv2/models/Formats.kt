package org.hyperledger.ariesframework.credentialsv2.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
class Formats(
    @SerialName("attach_id")
    var attachId: String? = null,

    @SerialName("format")
    var format: String? = null

)  {

    fun constructor(options: FormatSpec) {
        this.attachId = options.attachmentId // ?? utils.uuid()
        this.format = options.format
    }
}
