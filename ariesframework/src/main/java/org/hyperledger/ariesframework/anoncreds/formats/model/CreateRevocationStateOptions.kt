package org.hyperledger.ariesframework.anoncreds.formats.model

import anoncreds_uniffi.CredentialRevocationState
import anoncreds_uniffi.RevocationRegistryDefinition
import anoncreds_uniffi.RevocationStatusList

data class CreateRevocationStateOptions(
    val revocationRegistryDefinition: RevocationRegistryDefinition,
    val revocationStatusList: RevocationStatusList,
    val revocationRegistryIndex: Int,
    val tailsPath: String,
    val oldRevocationStatusList: RevocationStatusList? = null,
    val oldRevocationState: CredentialRevocationState? = null,
)
