package org.hyperledger.ariesframework.anoncreds

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsModuleConfigOptions
import org.hyperledger.ariesframework.anoncreds.service.tails.BasicTailsFileService
import org.hyperledger.ariesframework.anoncreds.service.tails.TailsFileService

class AnonCredsModuleConfig(
    val agent: Agent,
    private val options: AnonCredsModuleConfigOptions? = null,
) {

    val registries: List<AnonCredsRegistry>
        get() = options?.registries ?: emptyList()

    val tailsFileService: TailsFileService
        get() = options?.tailsFileService ?: BasicTailsFileService(agent)

//    val anoncreds: Any //AnonCredsLibrary
//        get() = options?.anoncreds

    val autoCreateLinkSecret: Boolean
        get() = options?.autoCreateLinkSecret ?: true
}
