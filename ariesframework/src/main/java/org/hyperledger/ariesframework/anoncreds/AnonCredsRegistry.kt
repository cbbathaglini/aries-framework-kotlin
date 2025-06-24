package org.hyperledger.ariesframework.anoncreds

import org.hyperledger.ariesframework.anoncreds.model.GetCredentialDefinitionReturn
import org.hyperledger.ariesframework.anoncreds.model.GetSchemaReturn

interface AnonCredsRegistry {

    /**
     * A name to identify the registry. Used for querying created objects.
     */
    val methodName: String

    val supportedIdentifier: Regex

    suspend fun getSchema(schemaId: String): GetSchemaReturn

//    suspend fun registerSchema(options: RegisterSchemaOptions): RegisterSchemaReturn
//
    suspend fun getCredentialDefinition(
        credentialDefinitionId: String
    ): GetCredentialDefinitionReturn

//    suspend fun registerCredentialDefinition(
//        options: RegisterCredentialDefinitionOptions
//    ): RegisterCredentialDefinitionReturn
//
    suspend fun getRevocationRegistryDefinition(
        revocationRegistryDefinitionId: String
    ): GetRevocationRegistryDefinitionReturn

//    suspend fun registerRevocationRegistryDefinition(
//        options: RegisterRevocationRegistryDefinitionOptions
//    ): RegisterRevocationRegistryDefinitionReturn
//
    suspend fun getRevocationStatusList(
        revocationRegistryId: String,
        timestamp: Long
    ): GetRevocationStatusListReturn
//
//    suspend fun registerRevocationStatusList(
//        options: RegisterRevocationStatusListOptions
//    ): RegisterRevocationStatusListReturn
}