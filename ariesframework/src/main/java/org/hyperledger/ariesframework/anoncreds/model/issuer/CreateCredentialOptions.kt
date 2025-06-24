package org.hyperledger.ariesframework.anoncreds.model.issuer

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialOffer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList

@Serializable
data class CreateCredentialOptions(
    val credentialOffer: AnonCredsCredentialOffer,
    val credentialRequest: AnonCredsCredentialRequest,
    val credentialValues: AnonCredsCredentialValues,
    val revocationRegistryDefinitionId: String? = null,
    val revocationStatusList: AnonCredsRevocationStatusList? = null,
    val revocationRegistryIndex: Int? = null
)

@Serializable
data class AnonCredsCredentialValue(
    val raw: String,
    val encoded: String
)

typealias AnonCredsCredentialValues = Map<String, AnonCredsCredentialValue>