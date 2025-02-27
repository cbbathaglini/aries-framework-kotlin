package org.hyperledger.ariesframework.credentials.v2.formats

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID


@Serializable
class Format(
    @SerialName("attach_id")
    var attachId: String? = null,

    @SerialName("format")
    var format: String

)  {

    fun constructor(options: FormatSpec) {
        this.attachId = options.attachmentId ?: UUID.randomUUID().toString()
        this.format = options.format
    }
}
