package org.hyperledger.ariesframework.anoncreds.model.holder

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsSelectedCredentials
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinitions
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistries
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchemas
import org.hyperledger.ariesframework.proofs.messages.v2.RequestPresentationMessageV2

@Serializable
data class CreateProofOptions(
    val requestMessage: RequestPresentationMessageV2,
    val proofRequest: AnonCredsProofRequest,
    val selectedCredentials: AnonCredsSelectedCredentials,
    val schemas: AnonCredsSchemas,
    val credentialDefinitions: AnonCredsCredentialDefinitions,
    val revocationRegistries: AnonCredsRevocationRegistries,
    val useUnqualifiedIdentifiers: Boolean? = null,
    val proofFormats: Map<String, JsonElement>? = emptyMap(),
)
