package org.hyperledger.ariesframework.anoncreds.model

import anoncreds_uniffi.RevocationRegistryDefinition
import uniffi.indy_besu_vdr.RevocationStatusList


data class RevocationRegistryBucket(
    val definition: RevocationRegistryDefinition,
    val tailsFilePath: String? = null,
    val revocationStatusLists: MutableMap<Long, RevocationStatusList>
)