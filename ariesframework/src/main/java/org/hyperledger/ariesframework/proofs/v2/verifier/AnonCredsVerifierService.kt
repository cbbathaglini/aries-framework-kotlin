package org.hyperledger.ariesframework.proofs.v2.verifier

interface AnonCredsVerifierService {
    suspend fun verifyProof(
        options: VerifyProofOptions,
    ): Boolean

    suspend fun verifyW3cPresentation(
        options: VerifyW3cPresentationOptions,
    ): Boolean
}
