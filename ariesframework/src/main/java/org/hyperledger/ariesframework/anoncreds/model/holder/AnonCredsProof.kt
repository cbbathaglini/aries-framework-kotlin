package org.hyperledger.ariesframework.anoncreds.model.holder

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AnonCredsProof(
    val requested_proof: RequestedProof,
    val proof: JsonElement,
    val identifiers: List<Identifier>
)

@Serializable
data class RequestedProof(
    val revealed_attrs: Map<String, RevealedAttr>,
    val revealed_attr_groups: Map<String, RevealedAttrGroup>? = null,
    val unrevealed_attrs: Map<String, SubProofIndex>,
    val self_attested_attrs: Map<String, String>,
    val predicates: Map<String, SubProofIndex>
)

@Serializable
data class RevealedAttr(
    val sub_proof_index: Int,
    val raw: String,
    val encoded: String
)

@Serializable
data class RevealedAttrGroup(
    val sub_proof_index: Int,
    val values: Map<String, RawEncodedValue>
)

@Serializable
data class RawEncodedValue(
    val raw: String,
    val encoded: String
)

@Serializable
data class SubProofIndex(
    val sub_proof_index: Int
)

@Serializable
data class Identifier(
    val schema_id: String,
    val cred_def_id: String,
    val rev_reg_id: String? = null,
    val timestamp: Long? = null
)