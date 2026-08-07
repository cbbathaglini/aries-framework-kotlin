package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsAcceptProposalProofFormat(
    val name: String? = null,
    val version: String? = null,
)
