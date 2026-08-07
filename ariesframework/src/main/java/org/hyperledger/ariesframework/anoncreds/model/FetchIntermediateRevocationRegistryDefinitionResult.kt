package org.hyperledger.ariesframework.anoncreds.model

@kotlinx.serialization.Serializable
data class FetchIntermediateRevocationRegistryDefinitionResult(
    val id: String? = null, // indy
    val issuerId: String,
    val revocDefType: String = "CL_ACCUM",
    val credDefId: String,
    val tag: String,
    val value: RevocationRegistryValue,
    var revocationRegistryDefinitionId: String? = null,
    val indyNamespace: String? = null,
)
