package org.hyperledger.ariesframework.anoncreds.service.tails

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition

@Serializable
data class UploadTailsFileResult (
    val revocationRegistryDefinition: AnonCredsRevocationRegistryDefinition,
    val revocationRegistryDefinitionId: String?
){
}