package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import anoncreds_uniffi.CredentialRevocationState
import uniffi.indy_besu_vdr.RevocationRegistryDefinition
import uniffi.indy_besu_vdr.RevocationStatusList

data class CreateRevocationStateOptions(
    val revocationRegistryDefinition: RevocationRegistryDefinition,
    val revocationStatusList: RevocationStatusList,
    val revocationRegistryIndex: Int,
    val tailsPath: String,
    val oldRevocationStatusList: RevocationStatusList? = null,
    val oldRevocationState: CredentialRevocationState? = null
)