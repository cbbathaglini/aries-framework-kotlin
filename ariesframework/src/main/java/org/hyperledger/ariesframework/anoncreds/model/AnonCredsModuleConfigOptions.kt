package org.hyperledger.ariesframework.anoncreds.model

import org.hyperledger.ariesframework.anoncreds.AnonCredsRegistry
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.EthrAnonCredsRegistry
import org.hyperledger.ariesframework.anoncreds.service.tails.TailsFileService

data class AnonCredsModuleConfigOptions(
    val registries: List<AnonCredsRegistry> = listOf(EthrAnonCredsRegistry()),
    val tailsFileService: TailsFileService? = null,
    val anoncreds: Any? = null, // [todo]Anoncreds from uniffi
    val autoCreateLinkSecret: Boolean? = true,
)
