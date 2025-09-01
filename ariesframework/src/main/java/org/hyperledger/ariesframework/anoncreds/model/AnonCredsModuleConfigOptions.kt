package org.hyperledger.ariesframework.anoncreds.model

import org.hyperledger.ariesframework.anoncreds.AnonCredsRegistry
import org.hyperledger.ariesframework.anoncreds.service.tails.TailsFileService

data class AnonCredsModuleConfigOptions(
    val registries: List<AnonCredsRegistry>,
    val tailsFileService: TailsFileService? = null,
    val anoncreds: Any, // [todo]Anoncreds from uniffi
    val autoCreateLinkSecret: Boolean? = true,
)
