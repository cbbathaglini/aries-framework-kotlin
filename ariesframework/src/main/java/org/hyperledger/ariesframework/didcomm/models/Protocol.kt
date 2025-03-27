package org.hyperledger.ariesframework.didcomm.models

import kotlinx.serialization.SerialName

data class Protocol(
    override val id: String,

    @SerialName("feature-type")
    override val type: String = TYPE,

    val roles: List<String>? = null,
) : Feature(id, type) {

    companion object {
        const val TYPE = "protocol"
    }

    constructor(options: ProtocolOptions) : this(
        id = options.id,
        roles = options.roles,
    )
}
