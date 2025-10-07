package org.hyperledger.ariesframework.proofs.verifier

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinitions
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProof
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchemas
import org.hyperledger.ariesframework.proofs.messages.v2.PresentationMessageV2
import org.hyperledger.ariesframework.proofs.messages.v2.RequestPresentationMessageV2

@Serializable
data class VerifyProofOptions(
    val proofRequest: AnonCredsProofRequest,
    val presentationMessage: PresentationMessageV2,
    val requestMessage: RequestPresentationMessageV2,
    val proof: AnonCredsProof,
    val schemas: AnonCredsSchemas,
    val credentialDefinitions: AnonCredsCredentialDefinitions,
    val revocationRegistries: Map<String, RevocationRegistryEntry>,
) {
    override fun toString(): String {
        return "VerifyProofOptions(proofRequest=$proofRequest, proof=$proof, schemas=$schemas, credentialDefinitions=$credentialDefinitions, revocationRegistries=$revocationRegistries)"
    }
}

@Serializable
data class RevocationRegistryEntry(
    val definition: AnonCredsRevocationRegistryDefinition,
    val revocationStatusLists: MutableMap<Long, AnonCredsRevocationStatusList>? = mutableMapOf<Long, AnonCredsRevocationStatusList>(),
) {
    override fun toString(): String {
        return "RevocationRegistryEntry(definition=$definition, revocationStatusLists=$revocationStatusLists)"
    }
}
