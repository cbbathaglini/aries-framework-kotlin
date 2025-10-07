package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ProofRole {
    @SerialName("verifier")
    Verifier,

    @SerialName("prover")
    Prover,
}
