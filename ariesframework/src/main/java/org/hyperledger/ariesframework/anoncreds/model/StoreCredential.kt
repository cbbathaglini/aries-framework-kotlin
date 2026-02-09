package org.hyperledger.ariesframework.anoncreds.model

import org.hyperledger.ariesframework.anoncreds.utils.Indyidentifiers
import org.slf4j.LoggerFactory
import java.util.UUID

class StoreCredential {

    companion object {
        fun getStoreCredentialOptions(
            options: StoreCredentialOptions,
            indyNamespace: String? = null,
        ): StoreCredentialOptions {
            val credentialRequestMetadata = options.credentialRequestMetadata
            val credentialDefinitionId = options.credentialDefinitionId
            val schema = options.schema
            val credential = options.credential
            val credentialDefinition = options.credentialDefinition
            var revocationRegistry: RevocationRegistryInfo? = options.revocationRegistry

            val credDefId =
                if (Indyidentifiers.isUnqualifiedCredentialDefinitionId(credentialDefinitionId)) {
                    Indyidentifiers.getQualifiedDidIndyDid(
                        credentialDefinitionId,
                        indyNamespace ?: "",
                    )
                } else {
                    credentialDefinitionId
                }

            val credDef =
                if (Indyidentifiers.isUnqualifiedDidIndyCredentialDefinition(credentialDefinition)) {
                    Indyidentifiers.getQualifiedDidIndyCredentialDefinition(
                        credentialDefinition,
                        indyNamespace ?: "",
                    )
                } else {
                    credentialDefinition
                }

            val schemaParam = if (Indyidentifiers.isUnqualifiedDidIndySchema(schema)) {
                Indyidentifiers.getQualifiedDidIndySchema(schema, indyNamespace ?: "")
            } else {
                schema
            }

            if (revocationRegistry != null) {
                val a = revocationRegistry.definition

//                if (Indyidentifiers.isUnqualifiedDidIndyRevocationRegistryDefinition(a)) {
//                    logger.info("getQualifiedDidIndyRevocationRegistryDefinition(1) ${Indyidentifiers.getQualifiedDidIndyRevocationRegistryDefinition(a, indyNamespace ?: "")}")
//                } else {
//                    logger.info("getQualifiedDidIndyRevocationRegistryDefinition(2) $a")
//                }

//                if (Indyidentifiers.isUnqualifiedRevocationRegistryId(revocationRegistry.id)) {
//                    logger.info("getQualifiedDidIndyDid(1) ${Indyidentifiers.getQualifiedDidIndyDid(revocationRegistry.id, indyNamespace ?: "")}")
//                } else {
//                    logger.info("getQualifiedDidIndyDid(2) ${revocationRegistry.id}")
//                }

                revocationRegistry.definition.let {
                    RevocationRegistryInfo(
                        definition = if (Indyidentifiers.isUnqualifiedDidIndyRevocationRegistryDefinition(it)) {
                            Indyidentifiers.getQualifiedDidIndyRevocationRegistryDefinition(
                                it,
                                indyNamespace ?: "",
                            )
                        } else {
                            it
                        },
                        id = if (Indyidentifiers.isUnqualifiedRevocationRegistryId(revocationRegistry.id)) {
                            Indyidentifiers.getQualifiedDidIndyDid(
                                revocationRegistry.id,
                                indyNamespace ?: "",
                            )
                        } else {
                            revocationRegistry.id
                        },
                    )
                }
            }

            val newOptions = StoreCredentialOptions(
                credentialId = UUID.randomUUID().toString(),
                credentialRequestMetadata = credentialRequestMetadata,
                credential = credential,
                credentialDefinitionId = credDefId,
                credentialDefinition = credDef,
                schema = schemaParam,
                schemaId = options.schemaId,
                revocationRegistry = revocationRegistry,
            )

            return options
        }
    }
}
