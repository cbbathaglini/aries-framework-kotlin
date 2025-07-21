package org.hyperledger.ariesframework.anoncreds.model

import com.google.gson.Gson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class KeyCorrectnessProof (
    val c: String,
    val xz_cap: String,
    val xr_cap: List<List<String>> = emptyList()
){
    companion object {
        fun convertMapToKeyCorrectnessProof(map: Map<String, Any>): KeyCorrectnessProof {

            val json = Gson().toJson(map)
            return Json.decodeFromString(json)
        }
    }

    override fun toString(): String {
        return "KeyCorrectnessProof(c='$c', xz_cap='$xz_cap', xr_cap=$xr_cap)"
    }


}
