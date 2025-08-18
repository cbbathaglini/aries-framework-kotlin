package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


@Serializable
data class AnonCredsProof(
    @SerialName("requested_proof")
    val requestedProof: RequestedProof,
    val proof: JsonElement?, // TS: any
    val identifiers: List<Identifier>
)

@Serializable
data class RequestedProof(
    @SerialName("revealed_attrs")
    val revealedAttrs: Map<String, RevealedAttr>,

    @SerialName("revealed_attr_groups")
    val revealedAttrGroups: Map<String, RevealedAttrGroup>? = null,

    @SerialName("unrevealed_attrs")
    val unrevealedAttrs: Map<String, SubProofIndexOnly>,

    @SerialName("self_attested_attrs")
    val selfAttestedAttrs: Map<String, String>,

    val predicates: Map<String, SubProofIndexOnly>
)

@Serializable
data class RevealedAttr(
    @SerialName("sub_proof_index")
    val subProofIndex: Int,
    val raw: String,
    val encoded: String
)

@Serializable
data class RevealedAttrGroup(
    @SerialName("sub_proof_index")
    val subProofIndex: Int,
    val values: Map<String, AttrValue>
)

@Serializable
data class AttrValue(
    val raw: String,
    val encoded: String
)

@Serializable
data class SubProofIndexOnly(
    @SerialName("sub_proof_index")
    val subProofIndex: Int
)

@Serializable
data class Identifier(
    @SerialName("schema_id")
    val schemaId: String,

    @SerialName("cred_def_id")
    val credDefId: String,

    @SerialName("rev_reg_id")
    val revRegId: String? = null,

    val timestamp: Long? = null
)