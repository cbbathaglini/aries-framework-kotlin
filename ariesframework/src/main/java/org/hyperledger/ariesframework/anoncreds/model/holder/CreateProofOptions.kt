package org.hyperledger.ariesframework.anoncreds.model.holder

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsSelectedCredentials
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinitions
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistries
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchemas

@Serializable
data class CreateProofOptions(
    val proofRequest: AnonCredsProofRequest,
    val selectedCredentials: AnonCredsSelectedCredentials,
    val schemas: AnonCredsSchemas,
    val credentialDefinitions: AnonCredsCredentialDefinitions,
    val revocationRegistries: AnonCredsRevocationRegistries,
    val useUnqualifiedIdentifiers: Boolean? = null
)