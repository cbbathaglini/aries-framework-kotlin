package org.hyperledger.ariesframework.anoncreds.model

import org.hyperledger.ariesframework.anoncreds.utils.Indyidentifiers
import java.util.UUID

class StoreCredential {

    companion object{
        fun getStoreCredentialOptions(
            options: StoreCredentialOptions,
            indyNamespace: String? = null
        ): StoreCredentialOptions {
            val credentialRequestMetadata = options.credentialRequestMetadata
            val credentialDefinitionId = options.credentialDefinitionId
            val schema = options.schema
            val credential = options.credential
            val credentialDefinition = options.credentialDefinition
            val revocationRegistry = options.revocationRegistry

            return StoreCredentialOptions(
                credentialId = UUID.randomUUID().toString(),
                credentialRequestMetadata = credentialRequestMetadata,
                credential = credential,
                credentialDefinitionId = if (Indyidentifiers.isUnqualifiedCredentialDefinitionId(credentialDefinitionId)) {
                    Indyidentifiers.getQualifiedDidIndyDid(credentialDefinitionId, indyNamespace ?: "")
                } else {
                    credentialDefinitionId
                },
                credentialDefinition = if (Indyidentifiers.isUnqualifiedDidIndyCredentialDefinition(credentialDefinition)) {
                    Indyidentifiers.getQualifiedDidIndyCredentialDefinition(credentialDefinition, indyNamespace ?: "")
                } else {
                    credentialDefinition
                },
                schema = if (Indyidentifiers.isUnqualifiedDidIndySchema(schema)) {
                    Indyidentifiers.getQualifiedDidIndySchema(schema, indyNamespace ?: "")
                } else {
                    schema
                },
                revocationRegistry = revocationRegistry?.definition?.let {
                    RevocationRegistryInfo(
                        definition = if (Indyidentifiers.isUnqualifiedDidIndyRevocationRegistryDefinition(it)) {
                            Indyidentifiers.getQualifiedDidIndyRevocationRegistryDefinition(it, indyNamespace ?: "")
                        } else {
                            it
                        },
                        id = if (Indyidentifiers.isUnqualifiedRevocationRegistryId(revocationRegistry.id)) {
                            Indyidentifiers.getQualifiedDidIndyDid(revocationRegistry.id, indyNamespace ?: "")
                        } else {
                            revocationRegistry.id
                        }
                    )
                }
            )
        }
    }
}