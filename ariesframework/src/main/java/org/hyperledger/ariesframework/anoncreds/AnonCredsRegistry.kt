package org.hyperledger.ariesframework.anoncreds

import org.hyperledger.ariesframework.anoncreds.model.GetCredentialDefinitionReturn
import org.hyperledger.ariesframework.anoncreds.model.GetSchemaReturn
import org.hyperledger.ariesframework.anoncreds.service.registry.GetRevocationStatusListReturn

interface AnonCredsRegistry {

    val methodName: String

    val supportedIdentifier: Regex

    suspend fun getSchema(schemaId: String): GetSchemaReturn

    suspend fun getCredentialDefinition(
        credentialDefinitionId: String,
    ): GetCredentialDefinitionReturn

    suspend fun getRevocationRegistryDefinition(
        revocationRegistryDefinitionId: String,
    ): GetRevocationRegistryDefinitionReturn

    suspend fun getRevocationStatusList(
        revocationRegistryId: String,
        timestamp: Long,
    ): GetRevocationStatusListReturn

//    suspend fun registerRevocationStatusList(
//        options: RegisterRevocationStatusListOptions
//    ): RegisterRevocationStatusListReturn

//    suspend fun registerCredentialDefinition(
//        options: RegisterCredentialDefinitionOptions
//    ): RegisterCredentialDefinitionReturn
//

//    suspend fun registerRevocationRegistryDefinition(
//        options: RegisterRevocationRegistryDefinitionOptions
//    ): RegisterRevocationRegistryDefinitionReturn
//

//    suspend fun registerSchema(options: RegisterSchemaOptions): RegisterSchemaReturn
//
}
