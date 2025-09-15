package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsSelectCredentialsForRequest(
    val input: AnonCredsGetCredentialsForProofRequestOptions,
    val output: AnonCredsSelectedCredentials,
)
