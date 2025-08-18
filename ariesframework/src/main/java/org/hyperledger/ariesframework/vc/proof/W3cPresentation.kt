package org.hyperledger.ariesframework.vc.proof

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.hyperledger.ariesframework.vc.model.W3cVerifiableCredential

@Serializable
data class W3cPresentation (
    var id: String? = null,
    var context: JsonObject? = null,
    var type: MutableList<String>? = mutableListOf(),
    var verifiableCredential: List<W3cVerifiableCredential>,
    var holder: HolderOption? = null
)

@Serializable
sealed class HolderOption {
    data class StringHolder(val value: String) : HolderOption()
    data class ObjectHolder(val id: String) : HolderOption()
}

