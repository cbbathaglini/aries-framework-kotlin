package org.hyperledger.ariesframework.vc.repository

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsRevocationRegistryDefinitionRecord
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.storage.Repository
import org.hyperledger.ariesframework.vc.model.W3cCredential

class W3cCredentialRepository (agent: Agent) : Repository<W3cCredentialRecord>(
    W3cCredentialRecord::class,
    agent,
)
