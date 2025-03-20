package org.hyperledger.ariesframework.agent.decorators


import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
class ProofFormat(
    @EncodeDefault
    val attach_id: String = "indy",
    @EncodeDefault
    val format: String = "hlindy/proof-req@v2.0"

){
    fun toJsonString(): String = Json.encodeToString(this)
}