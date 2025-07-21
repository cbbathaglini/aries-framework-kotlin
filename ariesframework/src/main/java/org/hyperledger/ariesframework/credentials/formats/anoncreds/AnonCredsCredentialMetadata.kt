package org.hyperledger.ariesframework.credentials.formats.anoncreds

import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsCredentialMetadata(
    val schemaId: String? = null,
    val credentialDefinitionId: String? = null,
    val revocationRegistryId: String? = null,
    val credentialRevocationId: String? = null
){

}

