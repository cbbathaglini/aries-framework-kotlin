package org.hyperledger.ariesframework.anoncreds.service

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.AnonCredsRegistry
import org.hyperledger.ariesframework.anoncreds.exception.AnonCredsError

/**
 * @internal
 * The AnonCreds registry service manages multiple {@link AnonCredsRegistry} instances
 * and returns the correct registry based on a given identifier
 */
class AnonCredsRegistryService(val agent: Agent) {

    fun getRegistryForIdentifier(identifier: String): AnonCredsRegistry {
        val registries = agent.anoncredsmodulesconfig.registries
        val registry = registries.find { it.supportedIdentifier.matches(identifier) }
        return registry ?: throw AnonCredsError("No AnonCredsRegistry registered for identifier '$identifier'")
    }
}