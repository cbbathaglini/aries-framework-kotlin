package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest

@Serializable
data class GetCredentialsForProofRequestOptions(
    val proofRequest: AnonCredsProofRequest,
    val attributeReferent: String,
    val start: Int? = null,
    val limit: Int? = null,
    val extraQuery: ReferentWalletQuery? = null,
    val chosenCredentialId: String? = null, //equals to w3c credential id
)

typealias WalletQuery = Map<String, JsonElement?>

@Serializable
data class ReferentWalletQuery(
    val referents: Map<String, WalletQuery>,
)
