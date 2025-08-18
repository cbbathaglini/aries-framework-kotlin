package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsRestriction(
    @SerialName("schema_id")
    val schemaId: String? = null,

    @SerialName("schema_issuer_did")
    val schemaIssuerDid: String? = null,

    @SerialName("schema_issuer_id")
    val schemaIssuerId: String? = null,

    @SerialName("schema_name")
    val schemaName: String? = null,

    @SerialName("schema_version")
    val schemaVersion: String? = null,

    @SerialName("issuer_did")
    val issuerDid: String? = null,

    @SerialName("issuer_id")
    val issuerId: String? = null,

    @SerialName("cred_def_id")
    val credentialDefinitionId: String? = null,

    @Transient
    val attributeMarkers: Map<String, Boolean> = emptyMap(),

    @Transient
    val attributeValues: Map<String, String> = emptyMap()
)