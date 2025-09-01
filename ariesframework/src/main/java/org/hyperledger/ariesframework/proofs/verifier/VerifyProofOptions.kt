package org.hyperledger.ariesframework.proofs.verifier

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinitions
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProof
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchemas

@Serializable
data class VerifyProofOptions(
    val proofRequest: AnonCredsProofRequest,
    val proof: AnonCredsProof,
    val schemas: AnonCredsSchemas,
    val credentialDefinitions: AnonCredsCredentialDefinitions,
    val revocationRegistries: Map<String, RevocationRegistryEntry>
)

@Serializable
data class RevocationRegistryEntry(
    val definition: AnonCredsRevocationRegistryDefinition,
    val revocationStatusLists: MutableMap<Long, AnonCredsRevocationStatusList>? = mutableMapOf<Long, AnonCredsRevocationStatusList>()
)