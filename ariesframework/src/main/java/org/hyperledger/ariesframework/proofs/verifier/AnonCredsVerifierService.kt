package org.hyperledger.ariesframework.proofs.verifier

interface AnonCredsVerifierService {
    suspend fun verifyProof(
        options: VerifyProofOptions,
    ): Boolean

    suspend fun verifyW3cPresentation(
        options: VerifyW3cPresentationOptions,
    ): Boolean
}
