package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsSchema(
    val issuerId: String,
    val name: String,
    val version: String,
    val attrNames: List<String>
)