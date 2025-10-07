package org.hyperledger.ariesframework.proofs.verifier

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinitions
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchemas
import org.hyperledger.ariesframework.vc.proof.CredentialWithRevocationMetadata
import org.hyperledger.ariesframework.vc.proof.W3cJsonLdVerifiablePresentation

@Serializable
data class VerifyW3cPresentationOptions(
    val proofRequest: AnonCredsProofRequest,
    val presentation: W3cJsonLdVerifiablePresentation,
    val schemas: AnonCredsSchemas,
    val credentialDefinitions: AnonCredsCredentialDefinitions,
    val credentialsWithRevocationMetadata: List<CredentialWithRevocationMetadata>,
)
